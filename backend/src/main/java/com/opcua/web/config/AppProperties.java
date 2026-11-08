package com.opcua.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "opcua.web")
public class AppProperties {

    private String dataDir = "./data";
    private String corsAllowedOrigins = "http://localhost:5173,http://127.0.0.1:5173";
    private long liveHeartbeatMs = 15_000L;

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public String getCorsAllowedOrigins() {
        return corsAllowedOrigins;
    }

    public void setCorsAllowedOrigins(String corsAllowedOrigins) {
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    public long getLiveHeartbeatMs() {
        return liveHeartbeatMs;
    }

    public void setLiveHeartbeatMs(long liveHeartbeatMs) {
        this.liveHeartbeatMs = liveHeartbeatMs;
    }
}
