package com.opcua.web.service;

import com.opcua.web.domain.DeviceConfig;
import com.opcua.web.domain.TagConfig;
import com.opcua.web.dto.DashboardView;
import com.opcua.web.dto.DeviceView;
import com.opcua.web.store.ConfigStore;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private final DeviceService deviceService;
    private final ConfigStore store;
    private final OpcUaConnectionManager connectionManager;
    private final PkiService pkiService;
    private final LiveService liveService;

    public DashboardService(
        DeviceService deviceService,
        ConfigStore store,
        OpcUaConnectionManager connectionManager,
        PkiService pkiService,
        LiveService liveService
    ) {
        this.deviceService = deviceService;
        this.store = store;
        this.connectionManager = connectionManager;
        this.pkiService = pkiService;
        this.liveService = liveService;
    }

    public DashboardView dashboard() {
        List<DeviceView> devices = deviceService.list();
        int enabled = (int) devices.stream().filter(DeviceView::enabled).count();
        int connected = connectionManager.activeConnectionCount();
        int secure = (int) devices.stream()
            .filter(d -> !"NONE".equalsIgnoreCase(d.securityPolicy()))
            .count();
        return store.read(data -> {
            int tags = data.getTags().size();
            int bacnetMapped = (int) data.getTags().stream()
                .filter(t -> t.getBacnetObjectType() != null && !t.getBacnetObjectType().isBlank())
                .count();
            return new DashboardView(
                devices.size(),
                enabled,
                connected,
                tags,
                liveService.totalMonitoredTags(),
                secure,
                bacnetMapped,
                devices,
                pkiService.sha256Fingerprint()
            );
        });
    }
}
