package com.opcua.web.service;

import com.opcua.web.bacnet.BacnetMappingProvider;
import com.opcua.web.bacnet.BacnetPointSink;
import com.opcua.web.bacnet.BacnetWritePort;
import com.opcua.web.domain.DeviceConfig;
import com.opcua.web.domain.TagConfig;
import com.opcua.web.domain.enums.AccessMode;
import com.opcua.web.domain.enums.BacnetObjectType;
import com.opcua.web.domain.enums.TagDataType;
import com.opcua.web.dto.BacnetMappingView;
import com.opcua.web.dto.BacnetObjectView;
import com.opcua.web.dto.BacnetWriteRequest;
import com.opcua.web.dto.TagValueView;
import com.opcua.web.dto.WriteRequest;
import com.opcua.web.dto.WriteResult;
import com.opcua.web.exception.ApiException;
import com.opcua.web.store.ConfigStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BacnetMappingService implements BacnetWritePort {

    private static final Logger log = LoggerFactory.getLogger(BacnetMappingService.class);

    private final ConfigStore store;
    private final OpcUaValueService valueService;
    private final List<BacnetMappingProvider> providers;
    private final List<BacnetPointSink> sinks;

    public BacnetMappingService(
        ConfigStore store,
        OpcUaValueService valueService,
        List<BacnetMappingProvider> providers,
        List<BacnetPointSink> sinks
    ) {
        this.store = store;
        this.valueService = valueService;
        this.providers = providers.stream()
            .sorted(Comparator.comparingInt(BacnetMappingProvider::order))
            .toList();
        this.sinks = sinks;
    }

    public BacnetMappingView mapping(String deviceId) {
        return store.read(data -> {
            DeviceConfig device = findDevice(data, deviceId);
            List<TagConfig> tags = data.getTags().stream()
                .filter(tag -> deviceId.equals(tag.getDeviceId()) && tag.isEnabled())
                .sorted(Comparator.comparing(TagConfig::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
            return buildMapping(device, tags, false);
        });
    }

    public BacnetMappingView apply(String deviceId) {
        return store.mutate(data -> {
            DeviceConfig device = findDevice(data, deviceId);
            List<TagConfig> tags = data.getTags().stream()
                .filter(tag -> deviceId.equals(tag.getDeviceId()) && tag.isEnabled())
                .sorted(Comparator.comparing(TagConfig::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
            MappingPlan plan = plan(device, tags);
            for (TagConfig tag : tags) {
                PlannedObject object = plan.byTagId().get(tag.getId());
                if (object != null) {
                    tag.setBacnetObjectType(object.objectType().name());
                    tag.setBacnetInstance(object.instance());
                    if (tag.getBacnetProperty() == null || tag.getBacnetProperty().isBlank()) {
                        tag.setBacnetProperty("present-value");
                    }
                }
            }
            return buildMapping(device, tags, false);
        });
    }

    public String toCsv(String deviceId) {
        BacnetMappingView mapping = mapping(deviceId);
        StringBuilder sb = new StringBuilder();
        sb.append("device,object_type,object_type_number,instance,object_id,property,object_name,node_id,data_type,units,writable,mapping_source\n");
        for (BacnetObjectView object : mapping.objects()) {
            sb.append(csv(mapping.deviceName())).append(',')
                .append(csv(object.objectType())).append(',')
                .append(object.objectTypeNumber()).append(',')
                .append(object.instanceNumber()).append(',')
                .append(csv(object.objectId())).append(',')
                .append(csv(object.property())).append(',')
                .append(csv(object.objectName())).append(',')
                .append(csv(object.nodeId())).append(',')
                .append(csv(object.dataType())).append(',')
                .append(csv(object.units())).append(',')
                .append("READ_WRITE".equals(object.accessMode())).append(',')
                .append(csv(object.mappingSource())).append('\n');
        }
        return sb.toString();
    }

    public void publish(TagConfig tag, TagValueView value) {
        if (sinks.isEmpty() || value == null) {
            return;
        }
        DeviceConfig device = store.read(data -> data.getDevices().stream()
            .filter(d -> d.getId().equals(tag.getDeviceId()))
            .findFirst()
            .orElse(null));
        if (device == null) {
            return;
        }
        List<TagConfig> tags = store.read(data -> data.getTags().stream()
            .filter(t -> t.getDeviceId().equals(device.getId()) && t.isEnabled())
            .toList());
        MappingPlan plan = plan(device, tags);
        PlannedObject mapped = plan.byTagId().get(tag.getId());
        if (mapped == null) {
            return;
        }
        publish(tag, value, device, mapped.objectType().name(),
            mapped.objectType().getObjectTypeNumber(), mapped.instance(), mapped.objectId());
    }

    /**
     * Efficient path used by the live subscription: the mapping was resolved once
     * when the monitored items were created.
     */
    public void publish(
        TagConfig tag,
        TagValueView value,
        DeviceConfig device,
        String objectType,
        int objectTypeNumber,
        int instance,
        String objectId
    ) {
        if (sinks.isEmpty() || value == null) {
            return;
        }
        BacnetPointSink.BacnetPointValue pointValue = new BacnetPointSink.BacnetPointValue(
            device.getId(),
            device.getName(),
            tag.getId(),
            objectType,
            objectTypeNumber,
            instance,
            objectId,
            normalizeProperty(tag.getBacnetProperty()),
            value.value(),
            value.quality(),
            value.sourceTime(),
            value.dataType(),
            tag.getUnit()
        );
        for (BacnetPointSink sink : sinks) {
            try {
                sink.onPointValue(pointValue);
            } catch (Exception e) {
                log.warn("BACnet PointSink 处理失败: {}", e.getMessage());
            }
        }
    }

    @Override
    public WriteResult write(BacnetWriteRequest request) {
        BacnetObjectType objectType = BacnetObjectType.fromName(request.objectType());
        if (objectType == null) {
            throw ApiException.badRequest("BACnet objectType 无效: " + request.objectType());
        }
        MappingPlan plan = store.read(data -> {
            DeviceConfig device = findDevice(data, request.deviceId());
            List<TagConfig> tags = data.getTags().stream()
                .filter(tag -> request.deviceId().equals(tag.getDeviceId()) && tag.isEnabled())
                .toList();
            return plan(device, tags);
        });
        if (request.instance() == null) {
            throw ApiException.badRequest("BACnet instance 不能为空");
        }
        PlannedObject matched = plan.objects().stream()
            .filter(o -> o.objectType() == objectType && o.instance() == request.instance())
            .findFirst()
            .orElseThrow(() -> ApiException.notFound(
                "找不到 BACnet 映射对象 " + objectType.name() + "," + request.instance()
            ));
        String requestedProperty = normalizeProperty(request.property());
        if (!"present-value".equals(requestedProperty)) {
            throw ApiException.badRequest("写入端口当前只支持 present-value 属性");
        }
        TagConfig tag = plan.tagsById().get(matched.tagId());
        if (tag == null) {
            throw ApiException.notFound("映射点位不存在");
        }
        AccessMode access = parseAccess(tag.getAccessMode());
        if (!access.canWrite()) {
            throw ApiException.badRequest("点位为只读，BACnet 写入被拒绝");
        }
        return valueService.write(
            request.deviceId(),
            new WriteRequest(tag.getId(), null, null, null, null, null, request.value())
        );
    }

    public Map<String, Object> integrationInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("status", "MAPPING_READY");
        info.put("protocolStack", "未内置 BACnet/IP 协议栈，仅提供对象映射和扩展接口");
        info.put("extensionPoints", List.of(
            "BacnetMappingProvider：自定义 OPC UA 数据类型到 BACnet 对象类型的映射",
            "BacnetPointSink：订阅值标准化后推送给外部 BACnet 服务端",
            "BacnetWritePort：外部 BACnet 写入回调到 OPC UA 点位"
        ));
        info.put("objectTypes", BacnetObjectType.values());
        info.put("properties", List.of(
            "present-value", "object-name", "description", "units", "cov-increment",
            "status-flags", "reliability", "out-of-service", "min-present-value", "max-present-value"
        ));
        return info;
    }

    private MappingPlan plan(DeviceConfig device, List<TagConfig> tags) {
        Map<String, PlannedObject> byTag = new LinkedHashMap<>();
        Map<String, TagConfig> tagsById = new HashMap<>();
        List<String> warnings = new ArrayList<>();
        Set<Integer> usedInstances = new HashSet<>();

        for (TagConfig tag : tags) {
            tagsById.put(tag.getId(), tag);
            if (tag.getBacnetInstance() != null && tag.getBacnetInstance() >= 0) {
                if (usedInstances.add(tag.getBacnetInstance())) {
                    // reserved
                } else {
                    warnings.add("点位 " + tag.getName() + " 的 BACnet 实例号 "
                        + tag.getBacnetInstance() + " 与其它点位冲突，生成时会重新分配");
                }
            }
        }

        int nextInstance = 1;
        for (TagConfig tag : tags) {
            BacnetObjectType type = mapType(tag, device, warnings);
            int instance;
            if (tag.getBacnetInstance() != null && tag.getBacnetInstance() >= 0
                && countInstance(tags, tag.getBacnetInstance()) == 1) {
                instance = tag.getBacnetInstance();
            } else {
                while (usedInstances.contains(nextInstance)) {
                    nextInstance++;
                }
                instance = nextInstance;
                usedInstances.add(instance);
            }
            String property = normalizeProperty(tag.getBacnetProperty());
            String objectId = objectId(type, instance);
            String source = tag.getBacnetObjectType() == null || tag.getBacnetObjectType().isBlank()
                ? "AUTO" : "OVERRIDE";
            List<String> objectWarnings = new ArrayList<>();
            if (tag.isArray()) {
                objectWarnings.add("数组点位映射为 CharacterStringValue，值以 JSON 数组字符串表示");
            }
            if (TagDataType.fromName(tag.getDataType()).orElse(TagDataType.AUTO) == TagDataType.AUTO) {
                objectWarnings.add("数据类型为 AUTO，建议扫描或读取属性后明确数据类型");
            }
            byTag.put(tag.getId(), new PlannedObject(
                tag.getId(), type, instance, objectId, property, source, objectWarnings
            ));
        }
        return new MappingPlan(device, tags, byTag, tagsById, warnings);
    }

    private int countInstance(List<TagConfig> tags, int instance) {
        return (int) tags.stream()
            .filter(t -> t.getBacnetInstance() != null && t.getBacnetInstance() == instance)
            .count();
    }

    private BacnetObjectType mapType(TagConfig tag, DeviceConfig device, List<String> warnings) {
        for (BacnetMappingProvider provider : providers) {
            try {
                BacnetObjectType type = provider.map(tag, device);
                if (type != null) {
                    return type;
                }
            } catch (Exception e) {
                warnings.add("BACnet 映射提供者 " + provider.getClass().getSimpleName()
                    + " 失败，已跳过: " + e.getMessage());
            }
        }
        return BacnetObjectType.CHARACTER_STRING_VALUE;
    }

    private BacnetMappingView buildMapping(DeviceConfig device, List<TagConfig> tags, boolean persisted) {
        MappingPlan plan = plan(device, tags);
        List<BacnetObjectView> objects = new ArrayList<>();
        List<String> warnings = new ArrayList<>(plan.warnings());
        for (TagConfig tag : tags) {
            PlannedObject mapped = plan.byTagId().get(tag.getId());
            if (mapped == null) continue;
            warnings.addAll(mapped.warnings());
            objects.add(new BacnetObjectView(
                tag.getId(),
                tag.getName(),
                tag.getNodeId(),
                tag.getDataType(),
                tag.isArray(),
                tag.getAccessMode(),
                mapped.objectType().name(),
                mapped.objectType().getObjectTypeNumber(),
                mapped.objectType().getChineseLabel() + " / " + mapped.objectType().getLabel(),
                mapped.instance(),
                mapped.objectId(),
                mapped.property(),
                tag.getName(),
                tag.getDescription(),
                tag.getUnit(),
                tag.getCovIncrement(),
                null,
                mapped.source(),
                mapped.warnings()
            ));
        }
        return new BacnetMappingView(
            device.getId(),
            device.getName(),
            objects.size(),
            objects,
            warnings,
            "映射预览。接入 BACnet/IP 协议栈后可通过 BacnetPointSink / BacnetWritePort 完成数据交换。"
        );
    }

    private DeviceConfig findDevice(com.opcua.web.domain.ConfigData data, String id) {
        return data.getDevices().stream()
            .filter(d -> d.getId().equals(id))
            .findFirst()
            .orElseThrow(() -> ApiException.notFound("设备不存在: " + id));
    }

    private static String objectId(BacnetObjectType type, int instance) {
        return type.name().toLowerCase(Locale.ROOT).replace('_', '-') + "," + instance;
    }

    private static String normalizeProperty(String value) {
        if (value == null || value.isBlank()) return "present-value";
        return value.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
    }

    private static AccessMode parseAccess(String value) {
        try {
            return AccessMode.valueOf(value);
        } catch (Exception e) {
            return AccessMode.READ_ONLY;
        }
    }

    private static String csv(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private record PlannedObject(
        String tagId,
        BacnetObjectType objectType,
        int instance,
        String objectId,
        String property,
        String source,
        List<String> warnings
    ) {
    }

    private record MappingPlan(
        DeviceConfig device,
        List<TagConfig> tags,
        Map<String, PlannedObject> byTagId,
        Map<String, TagConfig> tagsById,
        List<String> warnings
    ) {
        List<PlannedObject> objects() {
            return new ArrayList<>(byTagId.values());
        }
    }
}
