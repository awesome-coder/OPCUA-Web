package com.opcua.web.service;

import com.opcua.web.domain.DeviceConfig;
import com.opcua.web.dto.ConnectionTestResult;
import com.opcua.web.dto.DeviceStatusView;
import com.opcua.web.dto.EndpointView;
import com.opcua.web.event.ConfigChangedEvent;
import com.opcua.web.exception.ApiException;
import com.opcua.web.store.ConfigStore;
import com.opcua.web.util.Futures;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class OpcUaConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(OpcUaConnectionManager.class);

    private final ConfigStore store;
    private final OpcUaClientFactory clientFactory;
    private final ApplicationEventPublisher eventPublisher;

    private final Map<String, RuntimeConnection> connections = new ConcurrentHashMap<>();
    private final Map<String, String> lastErrors = new ConcurrentHashMap<>();
    private final Map<String, String> states = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public OpcUaConnectionManager(
        ConfigStore store,
        OpcUaClientFactory clientFactory,
        ApplicationEventPublisher eventPublisher
    ) {
        this.store = store;
        this.clientFactory = clientFactory;
        this.eventPublisher = eventPublisher;
    }

    public boolean isConnected(String deviceId) {
        return connections.containsKey(deviceId);
    }

    public OpcUaClient getClient(String deviceId) {
        RuntimeConnection connection = connections.get(deviceId);
        if (connection != null) {
            return connection.client();
        }
        connect(deviceId);
        RuntimeConnection established = connections.get(deviceId);
        if (established == null) {
            throw ApiException.badGateway("设备未连接: " + deviceId);
        }
        return established.client();
    }

    public DeviceStatusView connect(String deviceId) {
        ReentrantLock lock = locks.computeIfAbsent(deviceId, id -> new ReentrantLock());
        lock.lock();
        try {
            RuntimeConnection existing = connections.get(deviceId);
            if (existing != null) {
                return toStatus(deviceId, existing);
            }
            DeviceConfig device = loadDevice(deviceId);
            if (!device.isEnabled()) {
                throw ApiException.badRequest("设备已禁用，无法连接");
            }
            states.put(deviceId, "CONNECTING");
            lastErrors.remove(deviceId);
            OpcUaClient client = null;
            try {
                OpcUaClientFactory.PreparedClient prepared = clientFactory.prepare(device);
                client = prepared.client();
                Futures.await(client.connect(), connectionTimeout(device), "连接 OPC UA 会话");
                RuntimeConnection runtime = new RuntimeConnection(
                    device,
                    client,
                    prepared.endpoint(),
                    Instant.now()
                );
                connections.put(deviceId, runtime);
                states.put(deviceId, "CONNECTED");
                lastErrors.remove(deviceId);
                eventPublisher.publishEvent(new ConfigChangedEvent(deviceId, ConfigChangedEvent.ChangeType.DEVICE_CONNECTED));
                log.info("设备已连接: {} ({})", device.getName(), prepared.endpoint().getEndpointUrl());
                return toStatus(deviceId, runtime);
            } catch (Exception e) {
                if (client != null) {
                    try {
                        client.disconnect().get();
                    } catch (Exception ignored) {
                    }
                }
                String message = extractMessage(e);
                lastErrors.put(deviceId, message);
                states.put(deviceId, "ERROR");
                log.warn("设备连接失败: {} - {}", device.getName(), message);
                throw ApiException.badGateway(message);
            }
        } finally {
            lock.unlock();
        }
    }

    public void disconnect(String deviceId) {
        ReentrantLock lock = locks.computeIfAbsent(deviceId, id -> new ReentrantLock());
        lock.lock();
        try {
            RuntimeConnection runtime = connections.remove(deviceId);
            states.remove(deviceId);
            lastErrors.remove(deviceId);
            if (runtime != null) {
                try {
                    Futures.await(runtime.client().disconnect(), 10_000, "断开 OPC UA 会话");
                } catch (Exception e) {
                    log.warn("断开设备 {} 时发生错误: {}", deviceId, e.getMessage());
                }
                eventPublisher.publishEvent(
                    new ConfigChangedEvent(deviceId, ConfigChangedEvent.ChangeType.DEVICE_DISCONNECTED)
                );
                log.info("设备已断开: {}", runtime.device().getName());
            }
        } finally {
            lock.unlock();
        }
    }

    public DeviceStatusView status(String deviceId) {
        RuntimeConnection runtime = connections.get(deviceId);
        if (runtime != null) {
            return toStatus(deviceId, runtime);
        }
        String state = states.getOrDefault(deviceId, lastErrors.containsKey(deviceId) ? "ERROR" : "DISCONNECTED");
        return new DeviceStatusView(
            deviceId,
            state,
            false,
            null,
            null,
            null,
            null,
            null,
            lastErrors.get(deviceId),
            0
        );
    }

    public List<EndpointView> endpoints(String deviceId) {
        DeviceConfig device = loadDevice(deviceId);
        return clientFactory.toViews(clientFactory.discover(device));
    }

    public ConnectionTestResult test(DeviceConfig device) {
        long start = System.currentTimeMillis();
        OpcUaClient client = null;
        OpcUaClientFactory.PreparedClient prepared = null;
        try {
            prepared = clientFactory.prepare(device);
            client = prepared.client();
            Futures.await(client.connect(), connectionTimeout(device), "测试连接");
            EndpointDescription endpoint = prepared.endpoint();
            return new ConnectionTestResult(
                true,
                "连接成功",
                System.currentTimeMillis() - start,
                endpoint.getEndpointUrl(),
                endpoint.getSecurityPolicyUri(),
                endpoint.getSecurityMode() == null ? null : endpoint.getSecurityMode().name(),
                device.getAuthMode(),
                endpoint.getServer() == null || endpoint.getServer().getApplicationName() == null
                    ? null : endpoint.getServer().getApplicationName().getText(),
                endpoint.getServer() == null ? null : endpoint.getServer().getApplicationUri(),
                clientFactory.toViews(prepared.endpoints())
            );
        } catch (Exception e) {
            return new ConnectionTestResult(
                false,
                extractMessage(e),
                System.currentTimeMillis() - start,
                device.getEndpointUrl(),
                device.getSecurityPolicy(),
                device.getSecurityMode(),
                device.getAuthMode(),
                null,
                null,
                prepared == null ? List.of() : clientFactory.toViews(prepared.endpoints())
            );
        } finally {
            if (client != null) {
                try {
                    client.disconnect().get();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public int activeConnectionCount() {
        return connections.size();
    }

    @PreDestroy
    public void shutdown() {
        for (String id : List.copyOf(connections.keySet())) {
            disconnect(id);
        }
        try {
            org.eclipse.milo.opcua.stack.core.Stack.releaseSharedResources();
        } catch (Exception e) {
            log.debug("释放 Milo 共享资源失败: {}", e.getMessage());
        }
    }

    private DeviceStatusView toStatus(String deviceId, RuntimeConnection runtime) {
        EndpointDescription endpoint = runtime.endpoint();
        return new DeviceStatusView(
            deviceId,
            "CONNECTED",
            true,
            endpoint.getEndpointUrl(),
            endpoint.getSecurityPolicyUri(),
            endpoint.getSecurityMode() == null ? null : endpoint.getSecurityMode().name(),
            endpoint.getServer() == null || endpoint.getServer().getApplicationName() == null
                ? null : endpoint.getServer().getApplicationName().getText(),
            runtime.connectedAt(),
            lastErrors.get(deviceId),
            0
        );
    }

    private DeviceConfig loadDevice(String deviceId) {
        return store.read(data -> data.getDevices().stream()
            .filter(d -> d.getId().equals(deviceId))
            .findFirst()
            .map(this::copyDevice)
            .orElseThrow(() -> ApiException.notFound("设备不存在: " + deviceId)));
    }

    private DeviceConfig copyDevice(DeviceConfig source) {
        DeviceConfig copy = new DeviceConfig();
        copy.setId(source.getId());
        copy.setName(source.getName());
        copy.setDescription(source.getDescription());
        copy.setEndpointUrl(source.getEndpointUrl());
        copy.setSecurityPolicy(source.getSecurityPolicy());
        copy.setSecurityMode(source.getSecurityMode());
        copy.setAuthMode(source.getAuthMode());
        copy.setUsername(source.getUsername());
        copy.setPasswordEncrypted(source.getPasswordEncrypted());
        copy.setTrustAllCertificates(source.isTrustAllCertificates());
        copy.setServerCertificatePem(source.getServerCertificatePem());
        copy.setConnectTimeoutMs(source.getConnectTimeoutMs());
        copy.setRequestTimeoutMs(source.getRequestTimeoutMs());
        copy.setSessionTimeoutMs(source.getSessionTimeoutMs());
        copy.setPublishingIntervalMs(source.getPublishingIntervalMs());
        copy.setEnabled(source.isEnabled());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }

    private int connectionTimeout(DeviceConfig device) {
        return Math.max(5_000, device.getConnectTimeoutMs() + 5_000);
    }

    private static String extractMessage(Throwable throwable) {
        Throwable current = throwable;
        String best = throwable.getMessage();
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                best = current.getMessage();
            }
            current = current.getCause();
        }
        return best == null ? "未知错误" : best;
    }

    private record RuntimeConnection(
        DeviceConfig device,
        OpcUaClient client,
        EndpointDescription endpoint,
        Instant connectedAt
    ) {
    }
}
