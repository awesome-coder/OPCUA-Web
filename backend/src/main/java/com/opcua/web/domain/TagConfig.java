package com.opcua.web.domain;

import java.time.Instant;

public class TagConfig {

    private String id;
    private String deviceId;
    private String name;
    private String nodeId;
    private String dataType = "AUTO";
    private boolean array;
    private String accessMode = "READ_ONLY";
    private String unit;
    private String description;
    private double scale = 1.0d;
    private double offset = 0.0d;
    private double deadband = 0.0d;
    private int samplingIntervalMs = 0;
    private int queueSize = 1;
    private String enumValues;
    private String bacnetObjectType;
    private Integer bacnetInstance;
    private String bacnetProperty = "present-value";
    private Double covIncrement;
    private boolean enabled = true;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public boolean isArray() {
        return array;
    }

    public void setArray(boolean array) {
        this.array = array;
    }

    public String getAccessMode() {
        return accessMode;
    }

    public void setAccessMode(String accessMode) {
        this.accessMode = accessMode;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getScale() {
        return scale;
    }

    public void setScale(double scale) {
        this.scale = scale;
    }

    public double getOffset() {
        return offset;
    }

    public void setOffset(double offset) {
        this.offset = offset;
    }

    public double getDeadband() {
        return deadband;
    }

    public void setDeadband(double deadband) {
        this.deadband = deadband;
    }

    public int getSamplingIntervalMs() {
        return samplingIntervalMs;
    }

    public void setSamplingIntervalMs(int samplingIntervalMs) {
        this.samplingIntervalMs = samplingIntervalMs;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    public String getEnumValues() {
        return enumValues;
    }

    public void setEnumValues(String enumValues) {
        this.enumValues = enumValues;
    }

    public String getBacnetObjectType() {
        return bacnetObjectType;
    }

    public void setBacnetObjectType(String bacnetObjectType) {
        this.bacnetObjectType = bacnetObjectType;
    }

    public Integer getBacnetInstance() {
        return bacnetInstance;
    }

    public void setBacnetInstance(Integer bacnetInstance) {
        this.bacnetInstance = bacnetInstance;
    }

    public String getBacnetProperty() {
        return bacnetProperty;
    }

    public void setBacnetProperty(String bacnetProperty) {
        this.bacnetProperty = bacnetProperty;
    }

    public Double getCovIncrement() {
        return covIncrement;
    }

    public void setCovIncrement(Double covIncrement) {
        this.covIncrement = covIncrement;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
