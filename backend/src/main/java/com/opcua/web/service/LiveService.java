package com.opcua.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opcua.web.domain.DeviceConfig;
import com.opcua.web.domain.TagConfig;
import com.opcua.web.domain.enums.AccessMode;
import com.opcua.web.domain.enums.TagDataType;
import com.opcua.web.dto.BacnetMappingView;
import com.opcua.web.dto.BacnetObjectView;
import com.opcua.web.dto.LiveStatusView;
import com.opcua.web.dto.TagValueView;
import com.opcua.web.event.ConfigChangedEvent;
import com.opcua.web.exception.ApiException;
import com.opcua.web.store.ConfigStore;
import com.opcua.web.util.Futures;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.DataChangeTrigger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.DeadbandType;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.DataChangeFilter;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoredItemCreateRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoringParameters;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

@Service
public class LiveService {

    private static final Logger log = LoggerFactory.getLogger(LiveService.class);

    private final OpcUaConnectionManager connectionManager;
    private final TagService tagService;
    private final OpcUaValueService valueService;
    private final OpcUaValueConverter converter;
    private final BacnetMappingService bacnetMappingService;
    private final ConfigStore store;
    private final ObjectMapper objectMapper;

    private final Map<String, LiveSession> sessions = new ConcurrentHashMap<>();
    private final AtomicLong clientHandles = new AtomicLong(1);

    public LiveService(
        OpcUaConnectionManager connectionManager,
        TagService tagService,
        OpcUaValueService valueService,
        OpcUaValueConverter converter,
        BacnetMappingService bacnetMappingService,
        ConfigStore store,
        ObjectMapper objectMapper
    ) {
        this.connectionManager = connectionManager;
        this.tagService = tagService;
        this.valueService = valueService;
        this.converter = converter;
        this.bacnetMappingService = bacnetMappingService;
        this.store = store;
        this.objectMapper = objectMapper;
    }

    public SseEmitter open(String deviceId, List<String> requestedTagIds) {
        DeviceConfig device = loadDevice(deviceId);
        if (!device.isEnabled()) {
            throw ApiException.badRequest("设备已禁用，无法订阅");
        }
        OpcUaClient client = connectionManager.getClient(deviceId);
        List<TagConfig> tags = resolveTags(deviceId, requestedTagIds);
        if (tags.isEmpty()) {
            throw ApiException.badRequest("没有可订阅的已启用点位");
        }

        LiveSession session = sessions.computeIfAbsent(deviceId, id -> new LiveSession(device, client));
        synchronized (session) {
            if (session.closed) {
                sessions.remove(deviceId);
                session = new LiveSession(device, client);
                sessions.put(deviceId, session);
            }
        }

        SseEmitter emitter = new SseEmitter(0L);
        Set<String> tagIds = new LinkedHashSet<>();
        tags.forEach(tag -> tagIds.add(tag.getId()));
        session.emitters.put(emitter, tagIds);
        emitter.onCompletion(() -> removeEmitter(deviceId, emitter));
        emitter.onTimeout(() -> removeEmitter(deviceId, emitter));
        emitter.onError(error -> removeEmitter(deviceId, emitter));

        try {
            emitter.send(SseEmitter.event()
                .name("ready")
                .data(Map.of(
                    "deviceId", deviceId,
                    "deviceName", device.getName(),
                    "tagCount", tags.size(),
                    "time", Instant.now().toString()
                ), MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            session.emitters.remove(emitter);
            emitter.completeWithError(e);
            throw ApiException.badGateway("打开实时数据通道失败: " + e.getMessage());
        }

        rebuildAsync(session);
        return emitter;
    }

    public LiveStatusView status(String deviceId) {
        LiveSession session = sessions.get(deviceId);
        if (session == null) {
            return new LiveStatusView(deviceId, false, 0, 0, null, List.of(), null);
        }
        return new LiveStatusView(
            deviceId,
            !session.closed,
            session.emitters.size(),
            session.handleTags.size(),
            session.device.getPublishingIntervalMs() + "ms",
            new ArrayList<>(session.getDesiredTagIds()),
            session.lastError
        );
    }

    public int totalMonitoredTags() {
        return sessions.values().stream().mapToInt(session -> session.handleTags.size()).sum();
    }

    public void close(String deviceId) {
        LiveSession session = sessions.remove(deviceId);
        if (session != null) {
            closeSession(session, "实时通道已关闭");
        }
    }

    @EventListener
    public void onConfigChanged(ConfigChangedEvent event) {
        if (event.type() == ConfigChangedEvent.ChangeType.TAGS_CHANGED) {
            LiveSession session = sessions.get(event.deviceId());
            if (session != null) {
                rebuildAsync(session);
            }
        } else if (event.type() == ConfigChangedEvent.ChangeType.DEVICE_DISCONNECTED
            || event.type() == ConfigChangedEvent.ChangeType.DEVICE_UPDATED
            || event.type() == ConfigChangedEvent.ChangeType.DEVICE_DELETED) {
            close(event.deviceId());
        }
    }

    @Scheduled(fixedDelayString = "${opcua.web.live-heartbeat-ms:15000}")
    public void heartbeat() {
        for (LiveSession session : sessions.values()) {
            for (SseEmitter emitter : session.emitters.keySet()) {
                try {
                    emitter.send(SseEmitter.event().name("ping").data(Instant.now().toString()));
                } catch (Exception e) {
                    removeEmitter(session.device.getId(), emitter);
                }
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        for (String deviceId : List.copyOf(sessions.keySet())) {
            close(deviceId);
        }
    }

    private void rebuildAsync(LiveSession session) {
        if (session.emitters.isEmpty() || session.closed) {
            return;
        }
        Thread thread = new Thread(() -> rebuild(session), "opcua-live-rebuild-" + session.device.getId());
        thread.setDaemon(true);
        thread.start();
    }

    private void rebuild(LiveSession session) {
        synchronized (session) {
            if (session.closed) {
                return;
            }
            Set<String> desired = session.getDesiredTagIds();
            if (desired.isEmpty()) {
                closeSession(session, "没有订阅点位");
                return;
            }
            List<TagConfig> tags = store.read(data -> data.getTags().stream()
                .filter(tag -> session.device.getId().equals(tag.getDeviceId())
                    && tag.isEnabled() && desired.contains(tag.getId()))
                .toList());
            if (tags.isEmpty()) {
                closeSession(session, "订阅点位已删除或被禁用");
                return;
            }
            try {
                UaSubscription subscription = session.subscription;
                if (subscription == null) {
                    subscription = Futures.await(
                        session.client.getSubscriptionManager()
                            .createSubscription(session.device.getPublishingIntervalMs()),
                        session.device.getRequestTimeoutMs(),
                        "创建订阅"
                    );
                    session.subscription = subscription;
                }

                if (!session.items.isEmpty()) {
                    try {
                        Futures.await(subscription.deleteMonitoredItems(session.items),
                            5_000, "删除旧监控项");
                    } catch (Exception e) {
                        log.debug("删除旧监控项失败: {}", e.getMessage());
                    }
                    session.items.clear();
                    session.handleTags.clear();
                }

                session.tagsById.clear();
                for (TagConfig tag : tags) {
                    session.tagsById.put(tag.getId(), tag);
                }
                try {
                    BacnetMappingView mapping = bacnetMappingService.mapping(session.device.getId());
                    session.bacnetObjects.clear();
                    for (BacnetObjectView object : mapping.objects()) {
                        session.bacnetObjects.put(object.tagId(), object);
                    }
                } catch (Exception e) {
                    log.debug("加载 BACnet 映射缓存失败: {}", e.getMessage());
                }
                List<MonitoredItemCreateRequest> requests = new ArrayList<>();
                Map<UInteger, TagConfig> handleToTag = new HashMap<>();
                for (TagConfig tag : tags) {
                    NodeId nodeId;
                    try {
                        nodeId = NodeId.parse(tag.getNodeId());
                    } catch (Exception e) {
                        sendError(session, "点位 " + tag.getName() + " 的 NodeId 无效: " + tag.getNodeId());
                        continue;
                    }
                    UInteger handle = uint(clientHandles.getAndIncrement());
                    Double sampling = tag.getSamplingIntervalMs() > 0
                        ? (double) tag.getSamplingIntervalMs() : -1.0d;
                    ExtensionObject filter = buildFilter(session.client, tag);
                    MonitoringParameters parameters = new MonitoringParameters(
                        handle,
                        sampling,
                        filter,
                        uint(Math.max(1, tag.getQueueSize())),
                        true
                    );
                    requests.add(new MonitoredItemCreateRequest(
                        new ReadValueId(nodeId, AttributeId.Value.uid(), null, null),
                        MonitoringMode.Reporting,
                        parameters
                    ));
                    handleToTag.put(handle, tag);
                }

                if (requests.isEmpty()) {
                    sendError(session, "没有有效的订阅点位");
                    return;
                }

                List<UaMonitoredItem> items = Futures.await(
                    subscription.createMonitoredItems(TimestampsToReturn.Both, requests),
                    session.device.getRequestTimeoutMs(),
                    "创建监控项"
                );
                session.items.addAll(items);
                session.handleTags.putAll(handleToTag);
                for (UaMonitoredItem item : items) {
                    TagConfig tag = handleToTag.get(item.getClientHandle());
                    if (tag == null) {
                        continue;
                    }
                    item.setValueConsumer((monitoredItem, value) -> onValue(session, monitoredItem, value));
                }
                session.lastError = null;
                sendEvent(session, "subscribed", Map.of(
                    "tagCount", items.size(),
                    "publishingInterval", session.device.getPublishingIntervalMs(),
                    "time", Instant.now().toString()
                ));
                List<TagConfig> readable = tags.stream()
                    .filter(t -> parseAccess(t.getAccessMode()).canRead())
                    .toList();
                for (TagValueView value : valueService.readTags(session.client, session.device, readable)) {
                    broadcast(session, "value", value);
                    TagConfig tag = session.tagsById.get(value.tagId());
                    if (tag != null) {
                        publishBacnet(session, tag, value);
                    }
                }
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                session.lastError = message;
                log.warn("创建实时订阅失败: {}", message);
                sendError(session, "创建订阅失败: " + message);
            }
        }
    }

    private void onValue(LiveSession session, UaMonitoredItem item, DataValue value) {
        TagConfig tag = session.handleTags.get(item.getClientHandle());
        if (tag == null) {
            return;
        }
        TagValueView view = converter.toTagView(tag, value);
        broadcast(session, "value", view);
        publishBacnet(session, tag, view);
    }

    private void publishBacnet(LiveSession session, TagConfig tag, TagValueView value) {
        BacnetObjectView mapping = session.bacnetObjects.get(tag.getId());
        if (mapping == null) {
            return;
        }
        bacnetMappingService.publish(
            tag,
            value,
            session.device,
            mapping.objectType(),
            mapping.objectTypeNumber(),
            mapping.instanceNumber(),
            mapping.objectId()
        );
    }

    private ExtensionObject buildFilter(OpcUaClient client, TagConfig tag) {
        if (tag.getDeadband() <= 0.0d) {
            return null;
        }
        TagDataType type = TagDataType.fromName(tag.getDataType()).orElse(TagDataType.AUTO);
        if (!type.isNumeric()) {
            return null;
        }
        try {
            DataChangeFilter filter = new DataChangeFilter(
                DataChangeTrigger.StatusValue,
                uint(DeadbandType.Absolute.getValue()),
                tag.getDeadband()
            );
            return ExtensionObject.encode(client.getStaticSerializationContext(), filter);
        } catch (Exception e) {
            log.debug("构造死区过滤器失败: {}", e.getMessage());
            return null;
        }
    }

    private void removeEmitter(String deviceId, SseEmitter emitter) {
        LiveSession session = sessions.get(deviceId);
        if (session == null) {
            return;
        }
        session.emitters.remove(emitter);
        if (session.emitters.isEmpty()) {
            sessions.remove(deviceId, session);
            closeSession(session, null);
        } else {
            rebuildAsync(session);
        }
    }

    private void closeSession(LiveSession session, String message) {
        synchronized (session) {
            if (session.closed) {
                return;
            }
            session.closed = true;
            if (message != null) {
                sendEvent(session, "closed", Map.of("message", message, "time", Instant.now().toString()));
            }
            for (SseEmitter emitter : List.copyOf(session.emitters.keySet())) {
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                }
            }
            session.emitters.clear();
            UaSubscription subscription = session.subscription;
            session.subscription = null;
            if (subscription != null) {
                try {
                    Futures.await(
                        session.client.getSubscriptionManager()
                            .deleteSubscription(subscription.getSubscriptionId()),
                        5_000,
                        "删除订阅"
                    );
                } catch (Exception e) {
                    log.debug("删除订阅失败: {}", e.getMessage());
                }
            }
            session.items.clear();
            session.handleTags.clear();
        }
    }

    private void sendError(LiveSession session, String message) {
        sendEvent(session, "error", Map.of("message", message));
    }

    private void broadcast(LiveSession session, String event, Object data) {
        if (session.emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : List.copyOf(session.emitters.keySet())) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                removeEmitter(session.device.getId(), emitter);
            }
        }
    }

    private void sendEvent(LiveSession session, String event, Object data) {
        broadcast(session, event, data);
    }

    private List<TagConfig> resolveTags(String deviceId, List<String> requestedTagIds) {
        List<TagConfig> all = store.read(data -> data.getTags().stream()
            .filter(tag -> deviceId.equals(tag.getDeviceId()) && tag.isEnabled())
            .filter(tag -> requestedTagIds == null || requestedTagIds.isEmpty()
                || requestedTagIds.contains(tag.getId()))
            .toList());
        if (requestedTagIds != null && !requestedTagIds.isEmpty()) {
            Set<String> found = new HashSet<>();
            all.forEach(tag -> found.add(tag.getId()));
            List<String> missing = requestedTagIds.stream().filter(id -> !found.contains(id)).toList();
            if (!missing.isEmpty()) {
                log.debug("忽略不存在或禁用的订阅点位: {}", missing);
            }
        }
        return all;
    }

    private DeviceConfig loadDevice(String deviceId) {
        return store.read(data -> data.getDevices().stream()
            .filter(d -> d.getId().equals(deviceId))
            .findFirst()
            .orElseThrow(() -> ApiException.notFound("设备不存在: " + deviceId)));
    }

    private static AccessMode parseAccess(String value) {
        try {
            return AccessMode.valueOf(value);
        } catch (Exception e) {
            return AccessMode.READ_ONLY;
        }
    }

    private final class LiveSession {

        private final DeviceConfig device;
        private final OpcUaClient client;
        private final Map<SseEmitter, Set<String>> emitters = new ConcurrentHashMap<>();
        private final Map<UInteger, TagConfig> handleTags = new ConcurrentHashMap<>();
        private final Map<String, TagConfig> tagsById = new ConcurrentHashMap<>();
        private final Map<String, BacnetObjectView> bacnetObjects = new ConcurrentHashMap<>();
        private final List<UaMonitoredItem> items = new ArrayList<>();
        private volatile UaSubscription subscription;
        private volatile boolean closed;
        private volatile String lastError;

        private LiveSession(DeviceConfig device, OpcUaClient client) {
            this.device = device;
            this.client = client;
        }

        private Set<String> getDesiredTagIds() {
            Set<String> desired = new LinkedHashSet<>();
            Collection<Set<String>> values = emitters.values();
            for (Set<String> ids : values) {
                desired.addAll(ids);
            }
            return desired;
        }
    }
}
