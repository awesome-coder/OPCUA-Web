package com.opcua.web.service;

import com.opcua.web.domain.DeviceConfig;
import com.opcua.web.domain.enums.AuthMode;
import com.opcua.web.domain.enums.SecurityModeOption;
import com.opcua.web.domain.enums.SecurityPolicyOption;
import com.opcua.web.dto.EndpointView;
import com.opcua.web.exception.ApiException;
import com.opcua.web.util.Futures;
import com.opcua.web.util.PemUtil;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.sdk.client.api.identity.AnonymousProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.IdentityProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.UsernameProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.X509IdentityProvider;
import org.eclipse.milo.opcua.stack.client.DiscoveryClient;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.UserTokenType;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.UserTokenPolicy;
import org.eclipse.milo.opcua.stack.core.util.CertificateUtil;
import org.eclipse.milo.opcua.stack.core.util.EndpointUtil;
import org.springframework.stereotype.Component;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

@Component
public class OpcUaClientFactory {

    private final PkiService pkiService;
    private final SecretCipher secretCipher;

    public OpcUaClientFactory(PkiService pkiService, SecretCipher secretCipher) {
        this.pkiService = pkiService;
        this.secretCipher = secretCipher;
    }

    public List<EndpointDescription> discover(DeviceConfig device) {
        try {
            return Futures.await(
                DiscoveryClient.getEndpoints(device.getEndpointUrl()),
                device.getConnectTimeoutMs(),
                "获取服务器端点"
            );
        } catch (Exception e) {
            throw ApiException.badGateway("无法连接 OPC UA 服务端 " + device.getEndpointUrl() + ": " + e.getMessage());
        }
    }

    public OpcUaClient createConnected(DeviceConfig device) {
        return prepare(device).client();
    }

    public PreparedClient prepare(DeviceConfig device) {
        List<EndpointDescription> endpoints = discover(device);
        EndpointDescription endpoint = selectEndpoint(device, endpoints);
        return new PreparedClient(buildClient(device, endpoint), endpoint, endpoints);
    }

    public record PreparedClient(
        OpcUaClient client,
        EndpointDescription endpoint,
        List<EndpointDescription> endpoints
    ) {
    }

    public OpcUaClient buildClient(DeviceConfig device, EndpointDescription selectedEndpoint) {
        try {
            SecurityPolicyOption policyOption = SecurityPolicyOption.valueOf(device.getSecurityPolicy());
            SecurityPolicy policy = SecurityPolicy.valueOf(policyOption.getMiloName());
            MessageSecurityMode securityMode = MessageSecurityMode.valueOf(
                SecurityModeOption.valueOf(device.getSecurityMode()).getMiloName()
            );

            if (!policy.getUri().equals(selectedEndpoint.getSecurityPolicyUri())
                || securityMode != selectedEndpoint.getSecurityMode()) {
                throw ApiException.badRequest("选中的端点与配置的安全策略/安全模式不一致");
            }

            X509Certificate pinnedCertificate = null;
            if (device.getServerCertificatePem() != null && !device.getServerCertificatePem().isBlank()) {
                pinnedCertificate = PemUtil.parseCertificate(device.getServerCertificatePem());
            }
            PinnedCertificateValidator validator = new PinnedCertificateValidator(
                device.isTrustAllCertificates(), pinnedCertificate
            );

            IdentityProvider identityProvider = createIdentityProvider(device, validator);

            String host = EndpointUtil.getHost(device.getEndpointUrl());
            int port = EndpointUtil.getPort(device.getEndpointUrl());
            EndpointDescription connectEndpoint = EndpointUtil.updateUrl(selectedEndpoint, host, port);

            OpcUaClientConfigBuilder builder = new OpcUaClientConfigBuilder();
            builder.setApplicationName(LocalizedText.english(PkiService.APPLICATION_NAME));
            builder.setApplicationUri(PkiService.APPLICATION_URI);
            builder.setProductUri("urn:opcua-web:console");
            builder.setEndpoint(connectEndpoint);
            builder.setKeyPair(pkiService.getKeyPair());
            builder.setCertificate(pkiService.getCertificate());
            builder.setCertificateChain(new X509Certificate[]{pkiService.getCertificate()});
            builder.setCertificateValidator(validator);
            builder.setIdentityProvider(identityProvider);
            builder.setConnectTimeout(uint(device.getConnectTimeoutMs()));
            builder.setRequestTimeout(uint(device.getRequestTimeoutMs()));
            builder.setSessionTimeout(uint(device.getSessionTimeoutMs()));
            builder.setSessionName(() -> "OPCUA-Web:" + device.getName() + ":" + System.currentTimeMillis());

            return OpcUaClient.create(builder.build());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badGateway("创建 OPC UA 客户端失败: " + e.getMessage());
        }
    }

    private IdentityProvider createIdentityProvider(DeviceConfig device, PinnedCertificateValidator validator) {
        AuthMode authMode;
        try {
            authMode = AuthMode.valueOf(device.getAuthMode());
        } catch (Exception e) {
            authMode = AuthMode.ANONYMOUS;
        }
        return switch (authMode) {
            case ANONYMOUS -> new AnonymousProvider();
            case USERNAME -> {
                String password = secretCipher.decrypt(device.getPasswordEncrypted());
                yield new UsernameProvider(
                    device.getUsername() == null ? "" : device.getUsername(),
                    password == null ? "" : password,
                    validator
                );
            }
            case CERTIFICATE -> new X509IdentityProvider(
                pkiService.getCertificate(),
                pkiService.getPrivateKey()
            );
        };
    }

    public EndpointDescription selectEndpoint(DeviceConfig device, List<EndpointDescription> endpoints) {
        SecurityPolicyOption policyOption = SecurityPolicyOption.valueOf(device.getSecurityPolicy());
        SecurityPolicy policy = SecurityPolicy.valueOf(policyOption.getMiloName());
        MessageSecurityMode securityMode = MessageSecurityMode.valueOf(
            SecurityModeOption.valueOf(device.getSecurityMode()).getMiloName()
        );
        AuthMode authMode = AuthMode.valueOf(device.getAuthMode());

        List<EndpointDescription> matchingMode = endpoints.stream()
            .filter(e -> policy.getUri().equals(e.getSecurityPolicyUri()))
            .filter(e -> securityMode == e.getSecurityMode())
            .collect(Collectors.toList());

        if (matchingMode.isEmpty()) {
            throw new ApiException(
                org.springframework.http.HttpStatus.BAD_GATEWAY,
                "NO_MATCHING_ENDPOINT",
                "服务器没有提供匹配的端点（安全策略 " + policyOption.getLabel()
                    + " / 安全模式 " + device.getSecurityMode() + "）",
                Map.of("availableEndpoints", endpointSummaries(endpoints))
            );
        }

        List<EndpointDescription> matchingAuth = matchingMode.stream()
            .filter(e -> supportsAuth(e, authMode))
            .collect(Collectors.toList());

        if (matchingAuth.isEmpty()) {
            throw new ApiException(
                org.springframework.http.HttpStatus.BAD_GATEWAY,
                "NO_MATCHING_AUTH",
                "匹配安全策略的端点不支持认证方式 " + authMode.name()
                    + "。服务器可用用户令牌: " + matchingMode.stream()
                    .flatMap(e -> userTokenNames(e).stream()).distinct().collect(Collectors.joining(", ")),
                Map.of("availableEndpoints", endpointSummaries(matchingMode))
            );
        }

        return matchingAuth.get(0);
    }

    private boolean supportsAuth(EndpointDescription endpoint, AuthMode authMode) {
        return userTokenNames(endpoint).stream().anyMatch(name -> switch (authMode) {
            case ANONYMOUS -> name.equals(UserTokenType.Anonymous.name());
            case USERNAME -> name.equals(UserTokenType.UserName.name());
            case CERTIFICATE -> name.equals(UserTokenType.Certificate.name());
        });
    }

    private List<String> userTokenNames(EndpointDescription endpoint) {
        UserTokenPolicy[] policies = endpoint.getUserIdentityTokens();
        if (policies == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (UserTokenPolicy policy : policies) {
            if (policy.getTokenType() != null) {
                names.add(policy.getTokenType().name());
            }
        }
        return names;
    }

    public EndpointView toView(EndpointDescription endpoint) {
        String certificatePem = null;
        String fingerprint = null;
        try {
            if (endpoint.getServerCertificate() != null && !endpoint.getServerCertificate().isNull()) {
                X509Certificate certificate = CertificateUtil.decodeCertificate(endpoint.getServerCertificate().bytes());
                certificatePem = PemUtil.toPem(certificate);
                fingerprint = sha256(certificate);
            }
        } catch (Exception ignored) {
        }
        String policyName = SecurityPolicy.fromUriSafe(endpoint.getSecurityPolicyUri())
            .map(Enum::name)
            .orElse(endpoint.getSecurityPolicyUri());
        return new EndpointView(
            endpoint.getEndpointUrl(),
            policyName,
            endpoint.getSecurityMode() == null ? "Unknown" : endpoint.getSecurityMode().name(),
            endpoint.getSecurityLevel() == null ? 0 : endpoint.getSecurityLevel().intValue(),
            userTokenNames(endpoint),
            endpoint.getServer() == null || endpoint.getServer().getApplicationName() == null
                ? null : endpoint.getServer().getApplicationName().getText(),
            endpoint.getServer() == null ? null : endpoint.getServer().getApplicationUri(),
            endpoint.getServer() == null ? null : endpoint.getServer().getProductUri(),
            fingerprint,
            certificatePem,
            endpoint.getTransportProfileUri()
        );
    }

    public List<EndpointView> toViews(List<EndpointDescription> endpoints) {
        return endpoints.stream().map(this::toView).toList();
    }

    private List<Map<String, Object>> endpointSummaries(List<EndpointDescription> endpoints) {
        return endpoints.stream().map(e -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("endpointUrl", e.getEndpointUrl());
            item.put("securityPolicy", e.getSecurityPolicyUri());
            item.put("securityMode", e.getSecurityMode() == null ? null : e.getSecurityMode().name());
            item.put("userTokens", userTokenNames(e));
            return item;
        }).toList();
    }

    private static String sha256(X509Certificate certificate) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                if (!sb.isEmpty()) sb.append(':');
                sb.append(String.format("%02X", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
