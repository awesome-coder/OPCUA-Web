package com.opcua.web.service;

import com.opcua.web.domain.DeviceConfig;
import com.opcua.web.domain.TagConfig;
import com.opcua.web.domain.enums.AuthMode;
import com.opcua.web.domain.enums.SecurityModeOption;
import com.opcua.web.domain.enums.SecurityPolicyOption;
import com.opcua.web.dto.DeviceRequest;
import com.opcua.web.dto.DeviceStatusView;
import com.opcua.web.dto.DeviceView;
import com.opcua.web.event.ConfigChangedEvent;
import com.opcua.web.exception.ApiException;
import com.opcua.web.store.ConfigStore;
import com.opcua.web.util.Ids;
import com.opcua.web.util.PemUtil;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class DeviceService {

    private final ConfigStore store;
    private final SecretCipher secretCipher;
    private final ApplicationEventPublisher eventPublisher;
    private final OpcUaConnectionManager connectionManager;

    public DeviceService(
        ConfigStore store,
        SecretCipher secretCipher,
        ApplicationEventPublisher eventPublisher,
        OpcUaConnectionManager connectionManager
    ) {
        this.store = store;
        this.secretCipher = secretCipher;
        this.eventPublisher = eventPublisher;
        this.connectionManager = connectionManager;
    }

    public List<DeviceView> list() {
        return store.read(data -> data.getDevices().stream()
            .sorted(Comparator.comparing(DeviceConfig::getName, String.CASE_INSENSITIVE_ORDER))
            .map(this::toView)
            .toList());
    }

    public DeviceView get(String id) {
        return store.read(data -> toView(requireDevice(data, id)));
    }

    public DeviceConfig getEntity(String id) {
        return store.read(data -> copy(requireDevice(data, id)));
    }

    public DeviceView create(DeviceRequest request) {
        DeviceView view = store.mutate(data -> {
            validateRequest(request, null, data);
            DeviceConfig device = new DeviceConfig();
            device.setId(Ids.newId("dev"));
            device.setCreatedAt(Instant.now());
            applyRequest(device, request, true, data);
            data.getDevices().add(device);
            return toView(device);
        });
        eventPublisher.publishEvent(new ConfigChangedEvent(view.id(), ConfigChangedEvent.ChangeType.DEVICE_CREATED));
        return view;
    }

    public DeviceView update(String id, DeviceRequest request) {
        DeviceConfig before = getEntity(id);
        DeviceView view = store.mutate(data -> {
            DeviceConfig device = requireDevice(data, id);
            validateRequest(request, device, data);
            applyRequest(device, request, false, data);
            device.setUpdatedAt(Instant.now());
            return toView(device);
        });
        if (connectionManager.isConnected(id)) {
            connectionManager.disconnect(id);
        }
        eventPublisher.publishEvent(new ConfigChangedEvent(id, ConfigChangedEvent.ChangeType.DEVICE_UPDATED));
        return view;
    }

    public void delete(String id) {
        if (connectionManager.isConnected(id)) {
            connectionManager.disconnect(id);
        }
        store.mutate(data -> {
            DeviceConfig device = requireDevice(data, id);
            data.getDevices().remove(device);
            data.getTags().removeIf(tag -> id.equals(tag.getDeviceId()));
            return null;
        });
        eventPublisher.publishEvent(new ConfigChangedEvent(id, ConfigChangedEvent.ChangeType.DEVICE_DELETED));
    }

    public DeviceConfig buildTransient(DeviceRequest request, String existingId) {
        return store.read(data -> {
            validateRequest(request, existingId == null ? null : findDevice(data, existingId), data);
            DeviceConfig device = existingId == null ? null : findDevice(data, existingId);
            DeviceConfig result = new DeviceConfig();
            result.setId(existingId == null ? "test" : existingId);
            result.setCreatedAt(Instant.now());
            if (device != null) {
                result.setPasswordEncrypted(device.getPasswordEncrypted());
                result.setServerCertificatePem(device.getServerCertificatePem());
                result.setTrustAllCertificates(device.isTrustAllCertificates());
            }
            applyRequest(result, request, existingId == null, data);
            return result;
        });
    }

    public DeviceStatusView status(String id) {
        return connectionManager.status(id);
    }

    public ImportDevice upsertForImport(DeviceRequest request, boolean overwrite) {
        String name = request.name() == null ? "" : request.name().trim();
        DeviceView existing = store.read(data -> data.getDevices().stream()
            .filter(d -> d.getName().trim().equalsIgnoreCase(name))
            .findFirst()
            .map(this::toView)
            .orElse(null));
        if (existing != null && !overwrite) {
            return new ImportDevice(existing.id(), false, true, existing.name());
        }
        if (existing == null) {
            DeviceView created = create(request);
            return new ImportDevice(created.id(), true, false, created.name());
        }
        DeviceView updated = update(existing.id(), request);
        return new ImportDevice(updated.id(), false, false, updated.name());
    }

    public record ImportDevice(String id, boolean created, boolean skipped, String name) {
    }

    public int tagCount(String deviceId) {
        return store.read(data -> (int) data.getTags().stream()
            .filter(tag -> deviceId.equals(tag.getDeviceId()))
            .count());
    }

    private void validateRequest(DeviceRequest request, DeviceConfig existing, com.opcua.web.domain.ConfigData data) {
        String name = request.name().trim();
        boolean duplicate = data.getDevices().stream()
            .anyMatch(d -> !d.getId().equals(existing == null ? null : existing.getId())
                && d.getName().trim().equalsIgnoreCase(name));
        if (duplicate) {
            throw ApiException.conflict("设备名称已存在: " + name);
        }

        String url = request.endpointUrl().trim();
        if (!url.matches("^(opc\\.tcp|opc\\.https|opc\\.wss)://.+")) {
            throw ApiException.badRequest("端点地址必须以 opc.tcp://、opc.https:// 或 opc.wss:// 开头");
        }

        SecurityPolicyOption policy = request.securityPolicy();
        SecurityModeOption mode = request.securityMode();
        if (policy == SecurityPolicyOption.NONE && mode != SecurityModeOption.NONE) {
            throw ApiException.badRequest("安全策略为 None 时，安全模式必须为 None");
        }
        if (policy != SecurityPolicyOption.NONE && mode == SecurityModeOption.NONE) {
            throw ApiException.badRequest("启用安全策略时必须选择 Sign 或 SignAndEncrypt 安全模式");
        }
        if (policy != SecurityPolicyOption.NONE && !Boolean.TRUE.equals(request.trustAllCertificates())) {
            boolean effectiveTrustAll = request.trustAllCertificates() != null
                ? request.trustAllCertificates()
                : (existing != null && existing.isTrustAllCertificates());
            if (!effectiveTrustAll) {
                if (request.serverCertificatePem() == null || request.serverCertificatePem().isBlank()) {
                    boolean hasExistingPinned = existing != null && existing.getServerCertificatePem() != null
                        && !existing.getServerCertificatePem().isBlank();
                    if (!hasExistingPinned) {
                        throw ApiException.badRequest("关闭“信任所有服务器证书”后必须提供服务器证书 PEM 用于证书固定");
                    }
                }
            }
        }
        if (request.serverCertificatePem() != null && !request.serverCertificatePem().isBlank()) {
            try {
                PemUtil.parseCertificate(request.serverCertificatePem());
            } catch (Exception e) {
                throw ApiException.badRequest(e.getMessage());
            }
        }

        if (request.authMode() == AuthMode.USERNAME) {
            if (request.username() == null || request.username().isBlank()) {
                throw ApiException.badRequest("用户名认证方式必须填写用户名");
            }
            boolean hasExistingPassword = existing != null && existing.getPasswordEncrypted() != null;
            boolean providedPassword = request.password() != null && !request.password().isEmpty();
            if (!hasExistingPassword && !providedPassword) {
                throw ApiException.badRequest("用户名认证方式必须填写密码");
            }
        }
    }

    private void applyRequest(DeviceConfig device, DeviceRequest request, boolean create, com.opcua.web.domain.ConfigData data) {
        device.setName(request.name().trim());
        device.setDescription(trimToNull(request.description()));
        device.setEndpointUrl(request.endpointUrl().trim());
        device.setSecurityPolicy(request.securityPolicy().name());
        device.setSecurityMode(request.securityMode().name());
        device.setAuthMode(request.authMode().name());
        device.setUsername(request.authMode() == AuthMode.USERNAME ? trimToNull(request.username()) : null);
        if (request.password() != null) {
            device.setPasswordEncrypted(request.password().isEmpty() ? null : secretCipher.encrypt(request.password()));
        }
        if (request.authMode() != AuthMode.USERNAME) {
            device.setPasswordEncrypted(null);
        }
        device.setTrustAllCertificates(request.trustAllCertificates() == null
            ? (create || device.isTrustAllCertificates())
            : request.trustAllCertificates());
        if (request.serverCertificatePem() != null) {
            if (!request.serverCertificatePem().isBlank()) {
                device.setServerCertificatePem(request.serverCertificatePem().trim());
            } else if (create) {
                device.setServerCertificatePem(null);
            }
        }
        device.setConnectTimeoutMs(request.connectTimeoutMs() == null ? 10_000 : request.connectTimeoutMs());
        device.setRequestTimeoutMs(request.requestTimeoutMs() == null ? 15_000 : request.requestTimeoutMs());
        device.setSessionTimeoutMs(request.sessionTimeoutMs() == null ? 120_000 : request.sessionTimeoutMs());
        device.setPublishingIntervalMs(request.publishingIntervalMs() == null ? 1_000 : request.publishingIntervalMs());
        device.setEnabled(request.enabled() == null || request.enabled());
        if (create) {
            device.setUpdatedAt(device.getCreatedAt());
        }
    }

    private DeviceView toView(DeviceConfig device) {
        DeviceStatusView status = connectionManager.status(device.getId());
        int tagCount = Math.toIntExact(store.read(data -> data.getTags().stream()
            .filter(tag -> device.getId().equals(tag.getDeviceId()))
            .count()));
        return new DeviceView(
            device.getId(),
            device.getName(),
            device.getDescription(),
            device.getEndpointUrl(),
            device.getSecurityPolicy(),
            device.getSecurityMode(),
            device.getAuthMode(),
            device.getUsername(),
            device.getPasswordEncrypted() != null,
            device.isTrustAllCertificates(),
            device.getServerCertificatePem() != null,
            device.getConnectTimeoutMs(),
            device.getRequestTimeoutMs(),
            device.getSessionTimeoutMs(),
            device.getPublishingIntervalMs(),
            device.isEnabled(),
            device.getCreatedAt(),
            device.getUpdatedAt(),
            status.connected(),
            status.state(),
            status.lastError(),
            status.connectedAt(),
            tagCount
        );
    }

    private DeviceConfig requireDevice(com.opcua.web.domain.ConfigData data, String id) {
        return data.getDevices().stream()
            .filter(d -> d.getId().equals(id))
            .findFirst()
            .orElseThrow(() -> ApiException.notFound("设备不存在: " + id));
    }

    private DeviceConfig findDevice(com.opcua.web.domain.ConfigData data, String id) {
        return data.getDevices().stream()
            .filter(d -> d.getId().equals(id))
            .findFirst()
            .orElse(null);
    }

    public DeviceConfig copy(DeviceConfig source) {
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

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
