package com.opcua.web.controller;

import com.opcua.web.dto.BacnetMappingView;
import com.opcua.web.dto.BacnetWriteRequest;
import com.opcua.web.dto.WriteResult;
import com.opcua.web.service.BacnetMappingService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bacnet")
public class BacnetController {

    private final BacnetMappingService mappingService;

    public BacnetController(BacnetMappingService mappingService) {
        this.mappingService = mappingService;
    }

    @GetMapping("/integration")
    public Map<String, Object> integration() {
        return mappingService.integrationInfo();
    }

    @GetMapping("/devices/{deviceId}/objects")
    public BacnetMappingView objects(@PathVariable String deviceId) {
        return mappingService.mapping(deviceId);
    }

    @PostMapping("/devices/{deviceId}/apply")
    public BacnetMappingView apply(@PathVariable String deviceId) {
        return mappingService.apply(deviceId);
    }

    @GetMapping("/devices/{deviceId}/objects.csv")
    public ResponseEntity<byte[]> csv(@PathVariable String deviceId) {
        byte[] body = ("\uFEFF" + mappingService.toCsv(deviceId)).getBytes(StandardCharsets.UTF_8);
        String filename = "bacnet-objects-" + deviceId + ".csv";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        headers.setContentLength(body.length);
        return ResponseEntity.ok().headers(headers).body(body);
    }

    @PostMapping("/write")
    public WriteResult write(@Valid @RequestBody BacnetWriteRequest request) {
        return mappingService.write(request);
    }
}
