package com.opcua.web.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.opcua.web.config.AppProperties;
import com.opcua.web.domain.ConfigData;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Small JSON-backed configuration store. All reads/writes are serialized on this bean,
 * and writes use an atomic temp-file replace so a crash cannot leave a partial file.
 */
@Component
public class ConfigStore {

    private static final Logger log = LoggerFactory.getLogger(ConfigStore.class);

    private final ObjectMapper objectMapper;
    private final Path dataDir;
    private final Path dataFile;
    private ConfigData data = new ConfigData();

    public ConfigStore(ObjectMapper objectMapper, AppProperties properties) {
        this.objectMapper = objectMapper.copy()
            .enable(SerializationFeature.INDENT_OUTPUT);
        this.dataDir = Path.of(properties.getDataDir()).toAbsolutePath().normalize();
        this.dataFile = dataDir.resolve("config.json");
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(dataDir);
            if (Files.exists(dataFile)) {
                try {
                    data = objectMapper.readValue(dataFile.toFile(), ConfigData.class);
                    if (data.getDevices() == null) data.setDevices(new java.util.ArrayList<>());
                    if (data.getTags() == null) data.setTags(new java.util.ArrayList<>());
                    log.info("已加载配置: {} 台设备, {} 个点位 ({})",
                        data.getDevices().size(), data.getTags().size(), dataFile);
                } catch (Exception parseError) {
                    Path backup = dataDir.resolve("config.corrupt-" + Instant.now().toEpochMilli() + ".json");
                    Files.copy(dataFile, backup, StandardCopyOption.REPLACE_EXISTING);
                    data = new ConfigData();
                    log.error("配置文件损坏，已备份到 {}，将以空配置启动", backup, parseError);
                }
            } else {
                persist();
                log.info("已创建配置文件: {}", dataFile);
            }
        } catch (Exception e) {
            throw new IllegalStateException("初始化配置存储失败: " + e.getMessage(), e);
        }
    }

    public synchronized <T> T read(Function<ConfigData, T> reader) {
        return reader.apply(data);
    }

    public synchronized <T> T mutate(Function<ConfigData, T> mutator) {
        T result = mutator.apply(data);
        persist();
        return result;
    }

    public synchronized void replaceAll(ConfigData next) {
        this.data = next;
        persist();
    }

    public synchronized <T> T supply(Supplier<T> supplier) {
        return supplier.get();
    }

    public Path getDataDir() {
        return dataDir;
    }

    public Path getDataFile() {
        return dataFile;
    }

    public synchronized void persist() {
        try {
            Path temp = dataDir.resolve("config.json.tmp");
            objectMapper.writeValue(temp.toFile(), data);
            try {
                Files.move(temp, dataFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, dataFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            throw new IllegalStateException("保存配置失败: " + e.getMessage(), e);
        }
    }

    public synchronized String exportJson() {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new IllegalStateException("导出配置失败", e);
        }
    }

    public synchronized void importJson(String json) {
        try {
            ConfigData next = objectMapper.readValue(json.getBytes(StandardCharsets.UTF_8), ConfigData.class);
            this.data = next;
            persist();
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 配置格式无效", e);
        }
    }
}
