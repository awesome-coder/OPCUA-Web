package com.opcua.web.service;

import com.opcua.web.domain.TagConfig;
import com.opcua.web.domain.enums.AccessMode;
import com.opcua.web.domain.enums.BacnetObjectType;
import com.opcua.web.domain.enums.TagDataType;
import com.opcua.web.dto.BatchTagResult;
import com.opcua.web.dto.TagRequest;
import com.opcua.web.dto.TagView;
import com.opcua.web.event.ConfigChangedEvent;
import com.opcua.web.exception.ApiException;
import com.opcua.web.store.ConfigStore;
import com.opcua.web.util.Ids;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class TagService {

    private final ConfigStore store;
    private final ApplicationEventPublisher eventPublisher;

    public TagService(ConfigStore store, ApplicationEventPublisher eventPublisher) {
        this.store = store;
        this.eventPublisher = eventPublisher;
    }

    public List<TagView> list(String deviceId) {
        return store.read(data -> data.getTags().stream()
            .filter(tag -> deviceId == null || deviceId.isBlank() || deviceId.equals(tag.getDeviceId()))
            .sorted(Comparator.comparing(TagConfig::getName, String.CASE_INSENSITIVE_ORDER))
            .map(tag -> toView(data, tag))
            .toList());
    }

    public TagView get(String id) {
        return store.read(data -> toView(data, requireTag(data, id)));
    }

    public TagConfig getEntity(String id) {
        return store.read(data -> copy(requireTag(data, id)));
    }

    public TagConfig findEntity(String id) {
        return store.read(data -> {
            TagConfig tag = data.getTags().stream().filter(t -> t.getId().equals(id)).findFirst().orElse(null);
            return tag == null ? null : copy(tag);
        });
    }

    public List<TagConfig> listEnabledByDevice(String deviceId) {
        return store.read(data -> data.getTags().stream()
            .filter(tag -> deviceId.equals(tag.getDeviceId()) && tag.isEnabled())
            .sorted(Comparator.comparing(TagConfig::getName, String.CASE_INSENSITIVE_ORDER))
            .map(this::copy)
            .toList());
    }

    public List<TagConfig> listByDevice(String deviceId) {
        return store.read(data -> data.getTags().stream()
            .filter(tag -> deviceId.equals(tag.getDeviceId()))
            .map(this::copy)
            .toList());
    }

    public TagView create(TagRequest request) {
        TagView view = store.mutate(data -> {
            requireDevice(data, request.deviceId());
            validate(data, request, null);
            TagConfig tag = new TagConfig();
            tag.setId(Ids.newId("tag"));
            tag.setDeviceId(request.deviceId());
            tag.setCreatedAt(Instant.now());
            apply(tag, request);
            tag.setUpdatedAt(tag.getCreatedAt());
            data.getTags().add(tag);
            return toView(data, tag);
        });
        eventPublisher.publishEvent(new ConfigChangedEvent(view.deviceId(), ConfigChangedEvent.ChangeType.TAGS_CHANGED));
        return view;
    }

    public TagView update(String id, TagRequest request) {
        TagConfig before = getEntity(id);
        TagView view = store.mutate(data -> {
            TagConfig tag = requireTag(data, id);
            if (!Objects.equals(tag.getDeviceId(), request.deviceId())) {
                requireDevice(data, request.deviceId());
            }
            validate(data, request, tag);
            tag.setDeviceId(request.deviceId());
            apply(tag, request);
            tag.setUpdatedAt(Instant.now());
            return toView(data, tag);
        });
        eventPublisher.publishEvent(new ConfigChangedEvent(view.deviceId(), ConfigChangedEvent.ChangeType.TAGS_CHANGED));
        if (!Objects.equals(before.getDeviceId(), view.deviceId())) {
            eventPublisher.publishEvent(
                new ConfigChangedEvent(before.getDeviceId(), ConfigChangedEvent.ChangeType.TAGS_CHANGED)
            );
        }
        return view;
    }

    public void delete(String id) {
        String deviceId = store.mutate(data -> {
            TagConfig tag = requireTag(data, id);
            data.getTags().remove(tag);
            return tag.getDeviceId();
        });
        eventPublisher.publishEvent(new ConfigChangedEvent(deviceId, ConfigChangedEvent.ChangeType.TAGS_CHANGED));
    }

    public BatchTagResult batchCreate(List<TagRequest> requests) {
        List<TagView> created = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        String deviceId = requests.isEmpty() ? null : requests.get(0).deviceId();
        store.mutate(data -> {
            for (TagRequest request : requests) {
                try {
                    requireDevice(data, request.deviceId());
                    String uniqueName = uniqueName(data, request.deviceId(), request.name());
                    TagRequest effective = withName(request, uniqueName);
                    validate(data, effective, null, true);
                    TagConfig tag = new TagConfig();
                    tag.setId(Ids.newId("tag"));
                    tag.setDeviceId(effective.deviceId());
                    tag.setCreatedAt(Instant.now());
                    tag.setUpdatedAt(tag.getCreatedAt());
                    apply(tag, effective);
                    data.getTags().add(tag);
                    created.add(toView(data, tag));
                } catch (Exception e) {
                    errors.add(request.name() + ": " + e.getMessage());
                }
            }
            return null;
        });
        if (deviceId != null) {
            eventPublisher.publishEvent(new ConfigChangedEvent(deviceId, ConfigChangedEvent.ChangeType.TAGS_CHANGED));
        }
        requests.stream().map(TagRequest::deviceId).filter(Objects::nonNull).distinct()
            .filter(id -> !Objects.equals(id, deviceId))
            .forEach(id -> eventPublisher.publishEvent(
                new ConfigChangedEvent(id, ConfigChangedEvent.ChangeType.TAGS_CHANGED)
            ));
        return new BatchTagResult(created, skipped, errors);
    }

    public void deleteByDevice(String deviceId) {
        store.mutate(data -> {
            data.getTags().removeIf(tag -> deviceId.equals(tag.getDeviceId()));
            return null;
        });
    }

    public TagConfig upsertForImport(TagRequest request, boolean overwrite) {
        TagView view = store.mutate(data -> {
            requireDevice(data, request.deviceId());
            TagConfig existing = data.getTags().stream()
                .filter(tag -> request.deviceId().equals(tag.getDeviceId())
                    && tag.getName().equalsIgnoreCase(request.name()))
                .findFirst()
                .orElse(null);
            if (existing != null && !overwrite) {
                return null;
            }
            if (existing == null) {
                validate(data, request, null);
                existing = new TagConfig();
                existing.setId(Ids.newId("tag"));
                existing.setDeviceId(request.deviceId());
                existing.setCreatedAt(Instant.now());
                data.getTags().add(existing);
            } else {
                validate(data, request, existing);
            }
            apply(existing, request);
            existing.setUpdatedAt(Instant.now());
            return toView(data, existing);
        });
        if (view != null) {
            eventPublisher.publishEvent(new ConfigChangedEvent(view.deviceId(), ConfigChangedEvent.ChangeType.TAGS_CHANGED));
        }
        return view == null ? null : getEntity(view.id());
    }

    private void validate(com.opcua.web.domain.ConfigData data, TagRequest request, TagConfig existing) {
        validate(data, request, existing, false);
    }

    private void validate(com.opcua.web.domain.ConfigData data, TagRequest request, TagConfig existing, boolean lenient) {
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty()) {
            throw ApiException.badRequest("点位名称不能为空");
        }
        if (nameExists(data, request.deviceId(), name, existing == null ? null : existing.getId())) {
            throw ApiException.conflict("点位名称已存在: " + name);
        }
        try {
            NodeId nodeId = NodeId.parse(request.nodeId().trim());
            if (nodeId.isNull()) {
                throw new IllegalArgumentException("NodeId 不能为空");
            }
        } catch (Exception e) {
            throw ApiException.badRequest("NodeId 格式无效: " + request.nodeId() + "（示例 ns=2;s=Demo.Tag）");
        }
        if (request.scale() != null && request.scale() == 0.0d) {
            throw ApiException.badRequest("缩放系数不能为 0");
        }
        if (request.bacnetObjectType() != null && !request.bacnetObjectType().isBlank()) {
            BacnetObjectType.fromName(request.bacnetObjectType());
        }
        if (request.bacnetInstance() != null && (request.bacnetInstance() < 0 || request.bacnetInstance() > 4_194_302)) {
            throw ApiException.badRequest("BACnet 实例号必须在 0..4194302 之间");
        }
    }

    private void apply(TagConfig tag, TagRequest request) {
        tag.setName(request.name().trim());
        tag.setNodeId(NodeId.parse(request.nodeId().trim()).toParseableString());
        tag.setDataType(request.dataType().name());
        tag.setArray(Boolean.TRUE.equals(request.array()));
        tag.setAccessMode(request.accessMode().name());
        tag.setUnit(trimToNull(request.unit()));
        tag.setDescription(trimToNull(request.description()));
        tag.setScale(request.scale() == null ? 1.0d : request.scale());
        tag.setOffset(request.offset() == null ? 0.0d : request.offset());
        tag.setDeadband(request.deadband() == null ? 0.0d : Math.max(0, request.deadband()));
        tag.setSamplingIntervalMs(request.samplingIntervalMs() == null ? 0 : Math.max(0, request.samplingIntervalMs()));
        tag.setQueueSize(request.queueSize() == null ? 1 : Math.max(1, request.queueSize()));
        tag.setEnumValues(trimToNull(request.enumValues()));
        tag.setBacnetObjectType(normalizeBacnetType(request.bacnetObjectType()));
        tag.setBacnetInstance(request.bacnetInstance());
        tag.setBacnetProperty(request.bacnetProperty() == null || request.bacnetProperty().isBlank()
            ? "present-value" : request.bacnetProperty().trim().toLowerCase(Locale.ROOT));
        tag.setCovIncrement(request.covIncrement());
        tag.setEnabled(request.enabled() == null || request.enabled());
    }

    private String normalizeBacnetType(String value) {
        if (value == null || value.isBlank() || "AUTO".equalsIgnoreCase(value)) {
            return null;
        }
        return BacnetObjectType.fromName(value).name();
    }

    private boolean nameExists(com.opcua.web.domain.ConfigData data, String deviceId, String name, String exceptId) {
        return data.getTags().stream()
            .anyMatch(tag -> tag.getDeviceId().equals(deviceId)
                && tag.getName().equalsIgnoreCase(name.trim())
                && (exceptId == null || !exceptId.equals(tag.getId())));
    }

    private String uniqueName(com.opcua.web.domain.ConfigData data, String deviceId, String requestedName) {
        String base = requestedName == null || requestedName.isBlank() ? "未命名点位" : requestedName.trim();
        if (!nameExists(data, deviceId, base, null)) {
            return base;
        }
        int suffix = 2;
        while (nameExists(data, deviceId, base + " (" + suffix + ")", null)) {
            suffix++;
        }
        return base + " (" + suffix + ")";
    }

    private TagRequest withName(TagRequest source, String name) {
        return new TagRequest(
            source.deviceId(), name, source.nodeId(), source.dataType(), source.array(),
            source.accessMode(), source.unit(), source.description(), source.scale(), source.offset(),
            source.deadband(), source.samplingIntervalMs(), source.queueSize(), source.enumValues(),
            source.bacnetObjectType(), source.bacnetInstance(), source.bacnetProperty(),
            source.covIncrement(), source.enabled()
        );
    }

    private TagView toView(com.opcua.web.domain.ConfigData data, TagConfig tag) {
        String deviceName = data.getDevices().stream()
            .filter(d -> d.getId().equals(tag.getDeviceId()))
            .map(d -> d.getName())
            .findFirst()
            .orElse("(设备已删除)");
        AccessMode accessMode = parseAccessMode(tag.getAccessMode());
        return new TagView(
            tag.getId(),
            tag.getDeviceId(),
            deviceName,
            tag.getName(),
            tag.getNodeId(),
            tag.getDataType(),
            tag.isArray(),
            accessMode.name(),
            accessMode.canWrite(),
            tag.getUnit(),
            tag.getDescription(),
            tag.getScale(),
            tag.getOffset(),
            tag.getDeadband(),
            tag.getSamplingIntervalMs(),
            tag.getQueueSize(),
            tag.getEnumValues(),
            tag.getBacnetObjectType(),
            tag.getBacnetInstance(),
            tag.getBacnetProperty(),
            tag.getCovIncrement(),
            tag.isEnabled(),
            tag.getCreatedAt(),
            tag.getUpdatedAt()
        );
    }

    private AccessMode parseAccessMode(String value) {
        try {
            return AccessMode.valueOf(value);
        } catch (Exception e) {
            return AccessMode.READ_ONLY;
        }
    }

    private TagConfig requireTag(com.opcua.web.domain.ConfigData data, String id) {
        return data.getTags().stream()
            .filter(tag -> tag.getId().equals(id))
            .findFirst()
            .orElseThrow(() -> ApiException.notFound("点位不存在: " + id));
    }

    private com.opcua.web.domain.DeviceConfig requireDevice(com.opcua.web.domain.ConfigData data, String id) {
        return data.getDevices().stream()
            .filter(d -> d.getId().equals(id))
            .findFirst()
            .orElseThrow(() -> ApiException.notFound("设备不存在: " + id));
    }

    public TagConfig copy(TagConfig source) {
        TagConfig copy = new TagConfig();
        copy.setId(source.getId());
        copy.setDeviceId(source.getDeviceId());
        copy.setName(source.getName());
        copy.setNodeId(source.getNodeId());
        copy.setDataType(source.getDataType());
        copy.setArray(source.isArray());
        copy.setAccessMode(source.getAccessMode());
        copy.setUnit(source.getUnit());
        copy.setDescription(source.getDescription());
        copy.setScale(source.getScale());
        copy.setOffset(source.getOffset());
        copy.setDeadband(source.getDeadband());
        copy.setSamplingIntervalMs(source.getSamplingIntervalMs());
        copy.setQueueSize(source.getQueueSize());
        copy.setEnumValues(source.getEnumValues());
        copy.setBacnetObjectType(source.getBacnetObjectType());
        copy.setBacnetInstance(source.getBacnetInstance());
        copy.setBacnetProperty(source.getBacnetProperty());
        copy.setCovIncrement(source.getCovIncrement());
        copy.setEnabled(source.isEnabled());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
