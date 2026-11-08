package com.opcua.web.dto;

import java.util.List;

public record DashboardView(
    int deviceCount,
    int enabledDeviceCount,
    int connectedDeviceCount,
    int tagCount,
    int subscriptionTagCount,
    int secureDeviceCount,
    int bacnetMappedCount,
    List<DeviceView> devices,
    String clientCertificateFingerprint
) {
}
