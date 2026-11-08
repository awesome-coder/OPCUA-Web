package com.opcua.web.controller;

import com.opcua.web.domain.TagConfig;
import com.opcua.web.dto.BatchTagRequest;
import com.opcua.web.dto.BatchTagResult;
import com.opcua.web.dto.BrowseResultView;
import com.opcua.web.dto.ConnectionTestResult;
import com.opcua.web.dto.DeviceRequest;
import com.opcua.web.dto.DeviceStatusView;
import com.opcua.web.dto.DeviceView;
import com.opcua.web.dto.EndpointView;
import com.opcua.web.dto.LiveStatusView;
import com.opcua.web.dto.NodeValueView;
import com.opcua.web.dto.ReadRequest;
import com.opcua.web.dto.ReadResultView;
import com.opcua.web.dto.ScanRequest;
import com.opcua.web.dto.ScanResultView;
import com.opcua.web.dto.TagRequest;
import com.opcua.web.dto.WriteRequest;
import com.opcua.web.dto.WriteResult;
import com.opcua.web.service.DeviceService;
import com.opcua.web.service.LiveService;
import com.opcua.web.service.OpcUaBrowseService;
import com.opcua.web.service.OpcUaConnectionManager;
import com.opcua.web.service.OpcUaValueService;
import com.opcua.web.service.TagService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;
    private final OpcUaConnectionManager connectionManager;
    private final OpcUaBrowseService browseService;
    private final OpcUaValueService valueService;
    private final LiveService liveService;
    private final TagService tagService;

    public DeviceController(
        DeviceService deviceService,
        OpcUaConnectionManager connectionManager,
        OpcUaBrowseService browseService,
        OpcUaValueService valueService,
        LiveService liveService,
        TagService tagService
    ) {
        this.deviceService = deviceService;
        this.connectionManager = connectionManager;
        this.browseService = browseService;
        this.valueService = valueService;
        this.liveService = liveService;
        this.tagService = tagService;
    }

    @GetMapping
    public List<DeviceView> list() {
        return deviceService.list();
    }

    @GetMapping("/{id}")
    public DeviceView get(@org.springframework.web.bind.annotation.PathVariable String id) {
        return deviceService.get(id);
    }

    @PostMapping
    public DeviceView create(@Valid @RequestBody DeviceRequest request) {
        return deviceService.create(request);
    }

    @PutMapping("/{id}")
    public DeviceView update(
        @org.springframework.web.bind.annotation.PathVariable String id,
        @Valid @RequestBody DeviceRequest request
    ) {
        return deviceService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@org.springframework.web.bind.annotation.PathVariable String id) {
        deviceService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test")
    public ConnectionTestResult test(
        @Valid @RequestBody DeviceRequest request,
        @RequestParam(required = false) String existingId
    ) {
        return connectionManager.test(deviceService.buildTransient(request, existingId));
    }

    @PostMapping("/{id}/connect")
    public DeviceStatusView connect(@org.springframework.web.bind.annotation.PathVariable String id) {
        return connectionManager.connect(id);
    }

    @PostMapping("/{id}/disconnect")
    public DeviceStatusView disconnect(@org.springframework.web.bind.annotation.PathVariable String id) {
        connectionManager.disconnect(id);
        return connectionManager.status(id);
    }

    @GetMapping("/{id}/status")
    public DeviceStatusView status(@org.springframework.web.bind.annotation.PathVariable String id) {
        return connectionManager.status(id);
    }

    @GetMapping("/{id}/endpoints")
    public List<EndpointView> endpoints(@org.springframework.web.bind.annotation.PathVariable String id) {
        return connectionManager.endpoints(id);
    }

    @GetMapping("/{id}/browse")
    public BrowseResultView browse(
        @org.springframework.web.bind.annotation.PathVariable String id,
        @RequestParam(required = false) String nodeId
    ) {
        return browseService.browse(id, nodeId);
    }

    @GetMapping("/{id}/inspect")
    public com.opcua.web.dto.BrowseNodeView inspect(
        @org.springframework.web.bind.annotation.PathVariable String id,
        @RequestParam String nodeId
    ) {
        return browseService.inspect(id, nodeId);
    }

    @PostMapping("/{id}/scan")
    public ScanResultView scan(
        @org.springframework.web.bind.annotation.PathVariable String id,
        @RequestBody(required = false) @Valid ScanRequest request
    ) {
        return browseService.scan(id, request);
    }

    @PostMapping("/{id}/read")
    public ReadResultView read(
        @org.springframework.web.bind.annotation.PathVariable String id,
        @RequestBody(required = false) ReadRequest request
    ) {
        return valueService.read(id, request == null ? new ReadRequest(List.of(), List.of()) : request);
    }

    @PostMapping("/{id}/write")
    public WriteResult write(
        @org.springframework.web.bind.annotation.PathVariable String id,
        @Valid @RequestBody WriteRequest request
    ) {
        return valueService.write(id, request);
    }

    @GetMapping(value = "/{id}/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter live(
        @org.springframework.web.bind.annotation.PathVariable String id,
        @RequestParam(required = false) List<String> tagIds
    ) {
        return liveService.open(id, tagIds);
    }

    @GetMapping("/{id}/live/status")
    public LiveStatusView liveStatus(@org.springframework.web.bind.annotation.PathVariable String id) {
        return liveService.status(id);
    }

    @DeleteMapping("/{id}/live")
    public ResponseEntity<Void> closeLive(@org.springframework.web.bind.annotation.PathVariable String id) {
        liveService.close(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/tags/batch")
    public BatchTagResult batchTags(
        @org.springframework.web.bind.annotation.PathVariable String id,
        @Valid @RequestBody BatchTagRequest request
    ) {
        for (TagRequest tag : request.tags()) {
            if (tag.deviceId() == null || tag.deviceId().isBlank()) {
                throw new IllegalArgumentException("点位缺少 deviceId");
            }
            if (!id.equals(tag.deviceId())) {
                throw new IllegalArgumentException("批量添加的点位必须属于当前设备");
            }
        }
        return tagService.batchCreate(request.tags());
    }

    @GetMapping("/{id}/node-value")
    public NodeValueView readSingleNode(
        @org.springframework.web.bind.annotation.PathVariable String id,
        @RequestParam String nodeId
    ) {
        List<NodeValueView> values = valueService.read(id, new ReadRequest(List.of(), List.of(nodeId))).nodes();
        return values.isEmpty() ? null : values.get(0);
    }
}
