package com.opcua.web.controller;

import com.opcua.web.config.AppProperties;
import com.opcua.web.dto.DashboardView;
import com.opcua.web.dto.MetaView;
import com.opcua.web.dto.PkiInfoView;
import com.opcua.web.service.DashboardService;
import com.opcua.web.service.MetaService;
import com.opcua.web.service.OpcUaConnectionManager;
import com.opcua.web.service.PkiService;
import com.opcua.web.store.ConfigStore;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MetaController {

    private final MetaService metaService;
    private final DashboardService dashboardService;
    private final PkiService pkiService;
    private final OpcUaConnectionManager connectionManager;
    private final ConfigStore store;
    private final AppProperties properties;

    public MetaController(
        MetaService metaService,
        DashboardService dashboardService,
        PkiService pkiService,
        OpcUaConnectionManager connectionManager,
        ConfigStore store,
        AppProperties properties
    ) {
        this.metaService = metaService;
        this.dashboardService = dashboardService;
        this.pkiService = pkiService;
        this.connectionManager = connectionManager;
        this.store = store;
        this.properties = properties;
    }

    @GetMapping("/meta")
    public MetaView meta() {
        return metaService.meta();
    }

    @GetMapping("/dashboard")
    public DashboardView dashboard() {
        return dashboardService.dashboard();
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("time", Instant.now().toString());
        result.put("connectedDevices", connectionManager.activeConnectionCount());
        result.put("dataDir", store.getDataDir().toString());
        return result;
    }

    @GetMapping("/pki/info")
    public PkiInfoView pkiInfo() {
        return pkiService.info();
    }

    @GetMapping("/pki/client-certificate")
    public ResponseEntity<byte[]> clientCertificate() {
        byte[] body = pkiService.getCertificatePem().getBytes(StandardCharsets.UTF_8);
        String filename = "opcua-web-client-cert.pem";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/x-pem-file"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + filename + "\"");
        headers.setContentLength(body.length);
        return ResponseEntity.ok().headers(headers).body(body);
    }

    @GetMapping("/system")
    public Map<String, Object> system() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dataDir", store.getDataDir().toString());
        result.put("configFile", store.getDataFile().toString());
        result.put("pkiDir", pkiService.getPkiDir() == null ? null : pkiService.getPkiDir().toString());
        result.put("corsOrigins", properties.getCorsAllowedOrigins());
        result.put("clientCertificateFingerprint", pkiService.sha256Fingerprint());
        result.put("liveHeartbeatMs", properties.getLiveHeartbeatMs());
        return result;
    }
}
