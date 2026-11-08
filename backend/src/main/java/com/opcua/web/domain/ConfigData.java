package com.opcua.web.domain;

import java.util.ArrayList;
import java.util.List;

public class ConfigData {

    private int version = 1;
    private List<DeviceConfig> devices = new ArrayList<>();
    private List<TagConfig> tags = new ArrayList<>();

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public List<DeviceConfig> getDevices() {
        return devices;
    }

    public void setDevices(List<DeviceConfig> devices) {
        this.devices = devices == null ? new ArrayList<>() : devices;
    }

    public List<TagConfig> getTags() {
        return tags;
    }

    public void setTags(List<TagConfig> tags) {
        this.tags = tags == null ? new ArrayList<>() : tags;
    }
}
