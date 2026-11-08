package com.opcua.web.controller;

import com.opcua.web.dto.BatchTagRequest;
import com.opcua.web.dto.BatchTagResult;
import com.opcua.web.dto.TagRequest;
import com.opcua.web.dto.TagView;
import com.opcua.web.service.TagService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    public List<TagView> list(@RequestParam(required = false) String deviceId) {
        return tagService.list(deviceId);
    }

    @GetMapping("/{id}")
    public TagView get(@PathVariable String id) {
        return tagService.get(id);
    }

    @PostMapping
    public TagView create(@Valid @RequestBody TagRequest request) {
        return tagService.create(request);
    }

    @PutMapping("/{id}")
    public TagView update(@PathVariable String id, @Valid @RequestBody TagRequest request) {
        return tagService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        tagService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/batch")
    public BatchTagResult batch(@Valid @RequestBody BatchTagRequest request) {
        return tagService.batchCreate(request.tags());
    }
}
