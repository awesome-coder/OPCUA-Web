package com.opcua.web.service;

import com.opcua.web.domain.DeviceConfig;
import com.opcua.web.domain.TagConfig;
import com.opcua.web.domain.enums.AccessMode;
import com.opcua.web.domain.enums.TagDataType;
import com.opcua.web.dto.NodeValueView;
import com.opcua.web.dto.ReadRequest;
import com.opcua.web.dto.ReadResultView;
import com.opcua.web.dto.TagValueView;
import com.opcua.web.dto.WriteRequest;
import com.opcua.web.dto.WriteResult;
import com.opcua.web.exception.ApiException;
import com.opcua.web.store.ConfigStore;
import com.opcua.web.util.Futures;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.springframework.stereotype.Service;

@Service
public class OpcUaValueService {

    private final OpcUaConnectionManager connectionManager;
    private final TagService tagService;
    private final OpcUaValueConverter converter;
    private final ConfigStore store;

    public OpcUaValueService(
        OpcUaConnectionManager connectionManager,
        TagService tagService,
        OpcUaValueConverter converter,
        ConfigStore store
    ) {
        this.connectionManager = connectionManager;
        this.tagService = tagService;
        this.converter = converter;
        this.store = store;
    }

    public ReadResultView read(String deviceId, ReadRequest request) {
        DeviceConfig device = loadDevice(deviceId);
        OpcUaClient client = connectionManager.getClient(deviceId);
        List<TagConfig> tags = new ArrayList<>();
        if (request != null && request.tagIds() != null) {
            for (String tagId : request.tagIds()) {
                TagConfig tag = tagService.getEntity(tagId);
                if (!deviceId.equals(tag.getDeviceId())) {
                    throw ApiException.badRequest("点位不属于当前设备: " + tag.getName());
                }
                if (AccessMode.valueOf(tag.getAccessMode()).canRead()) {
                    tags.add(tag);
                }
            }
        }
        List<String> nodeIds = request == null || request.nodeIds() == null ? List.of() : request.nodeIds();
        List<TagValueView> tagValues = readTags(client, device, tags);
        List<NodeValueView> nodeValues = readNodes(client, device, nodeIds);
        return new ReadResultView(deviceId, tagValues, nodeValues, null);
    }

    public List<TagValueView> readTags(OpcUaClient client, DeviceConfig device, List<TagConfig> tags) {
        if (tags.isEmpty()) {
            return List.of();
        }
        List<NodeId> nodeIds = new ArrayList<>();
        for (TagConfig tag : tags) {
            try {
                nodeIds.add(NodeId.parse(tag.getNodeId()));
            } catch (Exception e) {
                nodeIds.add(NodeId.NULL_VALUE);
            }
        }
        List<TagValueView> result = new ArrayList<>();
        try {
            List<DataValue> values = Futures.await(
                client.readValues(0, TimestampsToReturn.Both, nodeIds),
                device.getRequestTimeoutMs(),
                "批量读取点位"
            );
            for (int i = 0; i < tags.size(); i++) {
                TagConfig tag = tags.get(i);
                if (nodeIds.get(i).isNull()) {
                    result.add(converter.errorTagView(tag, "NodeId 格式无效"));
                } else {
                    result.add(converter.toTagView(tag, values.get(i)));
                }
            }
        } catch (Exception e) {
            for (TagConfig tag : tags) {
                result.add(converter.errorTagView(tag, e.getMessage()));
            }
        }
        return result;
    }

    public List<NodeValueView> readNodes(OpcUaClient client, DeviceConfig device, List<String> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return List.of();
        }
        List<NodeId> parsed = new ArrayList<>();
        List<NodeValueView> result = new ArrayList<>();
        for (String text : nodeIds) {
            try {
                parsed.add(NodeId.parse(text));
            } catch (Exception e) {
                parsed.add(NodeId.NULL_VALUE);
            }
        }
        List<NodeId> valid = parsed.stream().filter(id -> !id.isNull()).toList();
        if (valid.isEmpty()) {
            for (int i = 0; i < parsed.size(); i++) {
                result.add(converter.errorNodeView(nodeIds.get(i), "NodeId 格式无效"));
            }
            return result;
        }
        try {
            List<DataValue> values = Futures.await(
                client.readValues(0, TimestampsToReturn.Both, valid),
                device.getRequestTimeoutMs(),
                "批量读取节点"
            );
            int validIndex = 0;
            for (int i = 0; i < parsed.size(); i++) {
                if (parsed.get(i).isNull()) {
                    result.add(converter.errorNodeView(nodeIds.get(i), "NodeId 格式无效"));
                } else {
                    result.add(converter.toNodeView(nodeIds.get(i), values.get(validIndex++)));
                }
            }
        } catch (Exception e) {
            for (String nodeId : nodeIds) {
                result.add(converter.errorNodeView(nodeId, e.getMessage()));
            }
        }
        return result;
    }

    public WriteResult write(String deviceId, WriteRequest request) {
        DeviceConfig device = loadDevice(deviceId);
        OpcUaClient client = connectionManager.getClient(deviceId);
        TagConfig tag = null;
        String nodeIdText;
        TagDataType dataType;
        boolean array;
        double scale;
        double offset;

        if (request.tagId() != null && !request.tagId().isBlank()) {
            tag = tagService.getEntity(request.tagId());
            if (!deviceId.equals(tag.getDeviceId())) {
                throw ApiException.badRequest("点位不属于当前设备");
            }
            AccessMode accessMode = AccessMode.valueOf(tag.getAccessMode());
            if (!accessMode.canWrite()) {
                throw ApiException.badRequest("点位 '" + tag.getName() + "' 配置为只读，已阻止写入");
            }
            nodeIdText = tag.getNodeId();
            dataType = TagDataType.fromName(tag.getDataType()).orElse(TagDataType.AUTO);
            array = tag.isArray();
            scale = tag.getScale();
            offset = tag.getOffset();
        } else if (request.nodeId() != null && !request.nodeId().isBlank()) {
            nodeIdText = request.nodeId();
            dataType = request.dataType() == null ? TagDataType.AUTO : request.dataType();
            array = Boolean.TRUE.equals(request.array());
            scale = request.scale() == null ? 1.0d : request.scale();
            offset = request.offset() == null ? 0.0d : request.offset();
        } else {
            throw ApiException.badRequest("必须提供 tagId 或 nodeId");
        }

        NodeId nodeId;
        try {
            nodeId = NodeId.parse(nodeIdText);
        } catch (Exception e) {
            throw ApiException.badRequest("NodeId 格式无效: " + nodeIdText);
        }
        if (nodeId.isNull()) {
            throw ApiException.badRequest("NodeId 不能为空");
        }

        NodeMetadata metadata = readNodeMetadata(client, nodeId, device.getRequestTimeoutMs());
        if (dataType == TagDataType.AUTO) {
            dataType = metadata.dataType() == TagDataType.AUTO ? metadata.valueType() : metadata.dataType();
        }
        if (dataType == TagDataType.AUTO) {
            throw ApiException.badRequest(
                "无法自动识别节点数据类型，请在点位配置中选择明确的数据类型后再写入"
            );
        }
        if (metadata.array()) {
            array = true;
        }

        Variant variant;
        try {
            variant = converter.toVariant(request.value(), dataType, array, scale, offset);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(e.getMessage());
        }

        try {
            StatusCode statusCode = Futures.await(
                client.writeValue(nodeId, DataValue.valueOnly(variant)),
                device.getRequestTimeoutMs(),
                "写入点位"
            );
            Object written = converter.toJson(variant.getValue());
            return new WriteResult(
                statusCode != null && statusCode.isGood(),
                nodeId.toParseableString(),
                tag == null ? null : tag.getId(),
                written,
                converter.quality(statusCode),
                statusCode == null ? 0 : statusCode.getValue(),
                converter.statusName(statusCode),
                statusCode != null && statusCode.isGood() ? "写入成功" : "服务器拒绝写入"
            );
        } catch (Exception e) {
            throw ApiException.badGateway("写入失败: " + e.getMessage());
        }
    }

    private NodeMetadata readNodeMetadata(OpcUaClient client, NodeId nodeId, int timeout) {
        try {
            var response = Futures.await(
                client.read(0, TimestampsToReturn.Neither, List.of(
                    new ReadValueId(nodeId, AttributeId.DataType.uid(), null, null),
                    new ReadValueId(nodeId, AttributeId.ValueRank.uid(), null, null),
                    new ReadValueId(nodeId, AttributeId.Value.uid(), null, null)
                )),
                timeout,
                "读取节点元数据"
            );
            NodeId dataTypeNode = null;
            Integer valueRank = null;
            TagDataType valueType = TagDataType.AUTO;
            DataValue[] results = response.getResults();
            if (results.length > 0 && results[0].getValue() != null && !results[0].getValue().isNull()
                && results[0].getValue().getValue() instanceof NodeId id) {
                dataTypeNode = id;
            }
            if (results.length > 1 && results[1].getValue() != null && !results[1].getValue().isNull()
                && results[1].getValue().getValue() instanceof Number number) {
                valueRank = number.intValue();
            }
            if (results.length > 2) {
                valueType = converter.inferFromVariant(results[2].getValue());
            }
            return new NodeMetadata(
                dataTypeNode == null ? TagDataType.AUTO : TagDataType.fromNodeId(dataTypeNode),
                valueRank != null && valueRank != -1,
                valueType
            );
        } catch (Exception e) {
            return new NodeMetadata(TagDataType.AUTO, false, TagDataType.AUTO);
        }
    }

    private DeviceConfig loadDevice(String deviceId) {
        return store.read(data -> data.getDevices().stream()
            .filter(d -> d.getId().equals(deviceId))
            .findFirst()
            .orElseThrow(() -> ApiException.notFound("设备不存在: " + deviceId)));
    }

    private record NodeMetadata(TagDataType dataType, boolean array, TagDataType valueType) {
    }
}
