package com.opcua.web.service;

import com.opcua.web.domain.DeviceConfig;
import com.opcua.web.dto.BrowseNodeView;
import com.opcua.web.dto.BrowseResultView;
import com.opcua.web.dto.ScanRequest;
import com.opcua.web.dto.ScanResultView;
import com.opcua.web.exception.ApiException;
import com.opcua.web.store.ConfigStore;
import com.opcua.web.util.Futures;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.eclipse.milo.opcua.sdk.client.BrowseHelper;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseResultMask;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;
import org.springframework.stereotype.Service;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

@Service
public class OpcUaBrowseService {

    private static final int MAX_BROWSE_RESULTS = 5_000;

    private final OpcUaConnectionManager connectionManager;
    private final ConfigStore store;

    public OpcUaBrowseService(OpcUaConnectionManager connectionManager, ConfigStore store) {
        this.connectionManager = connectionManager;
        this.store = store;
    }

    public BrowseResultView browse(String deviceId, String nodeIdText) {
        DeviceConfig device = loadDevice(deviceId);
        OpcUaClient client = connectionManager.getClient(deviceId);
        NodeId nodeId = parseNodeId(nodeIdText, Identifiers.ObjectsFolder);
        int timeout = device.getRequestTimeoutMs();

        String parentName = readDisplayName(client, nodeId, timeout);
        List<ReferenceDescription> references = browseChildren(client, nodeId, timeout);
        Map<NodeId, VariableAttributes> attributes = readVariableAttributes(
            client,
            references.stream().map(ref -> targetNodeId(client, ref)).filter(id -> id != null).toList(),
            timeout
        );

        List<BrowseNodeView> nodes = new ArrayList<>();
        for (ReferenceDescription reference : references) {
            NodeId target = targetNodeId(client, reference);
            if (target == null) continue;
            VariableAttributes attrs = attributes.get(target);
            nodes.add(toView(reference, target, attrs, parentName));
        }
        nodes.sort(Comparator
            .comparing((BrowseNodeView v) -> !"Object".equals(v.nodeClass()))
            .thenComparing(BrowseNodeView::displayName, String.CASE_INSENSITIVE_ORDER));
        return new BrowseResultView(nodeId.toParseableString(), parentName, nodes);
    }

    public BrowseNodeView inspect(String deviceId, String nodeIdText) {
        DeviceConfig device = loadDevice(deviceId);
        OpcUaClient client = connectionManager.getClient(deviceId);
        NodeId nodeId = parseNodeId(nodeIdText, null);
        int timeout = device.getRequestTimeoutMs();
        Map<AttributeId, DataValue> values = readAttributes(
            client,
            nodeId,
            List.of(
                AttributeId.NodeClass, AttributeId.BrowseName, AttributeId.DisplayName,
                AttributeId.Description, AttributeId.DataType, AttributeId.ValueRank,
                AttributeId.AccessLevel, AttributeId.UserAccessLevel
            ),
            timeout
        );
        NodeClass nodeClass = nodeClassValue(values.get(AttributeId.NodeClass), NodeClass.Variable);
        String displayName = textValue(values.get(AttributeId.DisplayName), nodeId.toParseableString());
        QualifiedName browseName = qualifiedNameValue(values.get(AttributeId.BrowseName));
        NodeId dataType = nodeIdValue(values.get(AttributeId.DataType));
        Integer valueRank = intValue(values.get(AttributeId.ValueRank));
        Integer accessLevel = ubyteInt(values.get(AttributeId.UserAccessLevel));
        if (accessLevel == null) {
            accessLevel = ubyteInt(values.get(AttributeId.AccessLevel));
        }
        return new BrowseNodeView(
            nodeId.toParseableString(),
            browseName == null ? displayName : browseName.getName(),
            displayName,
            nodeClass.name(),
            dataType == null ? null : dataType.toParseableString(),
            dataType == null ? null : com.opcua.web.domain.enums.TagDataType.displayName(dataType),
            dataType == null ? "AUTO" : com.opcua.web.domain.enums.TagDataType.fromNodeId(dataType).name(),
            valueRank,
            valueRank != null && valueRank != -1,
            accessLevel,
            accessLevel != null && (accessLevel & 0x02) != 0,
            textValue(values.get(AttributeId.Description), null),
            null,
            nodeClass == NodeClass.Object || nodeClass == NodeClass.Variable,
            displayName
        );
    }

    public ScanResultView scan(String deviceId, ScanRequest request) {
        long start = System.currentTimeMillis();
        DeviceConfig device = loadDevice(deviceId);
        OpcUaClient client = connectionManager.getClient(deviceId);
        int maxDepth = request == null || request.maxDepth() == null ? 4 : request.maxDepth();
        int maxNodes = request == null || request.maxNodes() == null ? 500 : request.maxNodes();
        boolean includeProperties = request != null && Boolean.TRUE.equals(request.includeProperties());
        NodeId rootNodeId = parseNodeId(request == null ? null : request.nodeId(), Identifiers.ObjectsFolder);
        String rootName = readDisplayName(client, rootNodeId, device.getRequestTimeoutMs());

        List<BrowseNodeView> tags = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Deque<ScanItem> queue = new ArrayDeque<>();
        queue.add(new ScanItem(rootNodeId, rootName, 0));
        visited.add(rootNodeId.toParseableString());
        int scannedObjects = 0;
        boolean truncated = false;

        while (!queue.isEmpty() && !truncated) {
            ScanItem item = queue.poll();
            if (item.depth() >= maxDepth) {
                continue;
            }
            List<ReferenceDescription> children;
            try {
                children = browseChildren(client, item.nodeId(), device.getRequestTimeoutMs());
            } catch (Exception e) {
                warnings.add("浏览 " + item.path() + " 失败: " + e.getMessage());
                continue;
            }
            List<ReadTarget> variables = new ArrayList<>();
            for (ReferenceDescription child : children) {
                NodeId target = targetNodeId(client, child);
                if (target == null) continue;
                if (!includeProperties && Identifiers.HasProperty.equals(child.getReferenceTypeId())) {
                    continue;
                }
                String name = displayName(child);
                String path = item.path() + "/" + name;
                if (child.getNodeClass() == NodeClass.Object) {
                    scannedObjects++;
                    if (visited.add(target.toParseableString())) {
                        queue.add(new ScanItem(target, path, item.depth() + 1));
                    }
                } else if (child.getNodeClass() == NodeClass.Variable) {
                    variables.add(new ReadTarget(child, target, path));
                }
            }
            if (!variables.isEmpty()) {
                Map<NodeId, VariableAttributes> attrs = readVariableAttributes(
                    client,
                    variables.stream().map(ReadTarget::nodeId).toList(),
                    device.getRequestTimeoutMs()
                );
                for (ReadTarget target : variables) {
                    if (tags.size() >= maxNodes) {
                        truncated = true;
                        break;
                    }
                    tags.add(toView(target.reference(), target.nodeId(),
                        attrs.get(target.nodeId()), item.path()));
                }
            }
            if (visited.size() > maxNodes * 4 + 100) {
                truncated = true;
                warnings.add("扫描对象数量过多，已提前停止");
            }
        }

        if (truncated) {
            warnings.add("已达到最大节点数 " + maxNodes + "，结果可能不完整");
        }
        tags.sort(Comparator.comparing(BrowseNodeView::path, String.CASE_INSENSITIVE_ORDER));
        return new ScanResultView(
            rootNodeId.toParseableString(),
            scannedObjects,
            tags.size(),
            truncated,
            System.currentTimeMillis() - start,
            tags,
            warnings
        );
    }

    private List<ReferenceDescription> browseChildren(OpcUaClient client, NodeId nodeId, int timeout) {
        BrowseDescription description = new BrowseDescription(
            nodeId,
            BrowseDirection.Forward,
            Identifiers.HierarchicalReferences,
            true,
            uint(0),
            uint(BrowseResultMask.All.getValue())
        );
        try {
            CompletableFuture<List<ReferenceDescription>> future = BrowseHelper.browse(client, description, uint(0));
            List<ReferenceDescription> references = Futures.await(future, timeout, "浏览节点 " + nodeId.toParseableString());
            if (references.size() > MAX_BROWSE_RESULTS) {
                return references.subList(0, MAX_BROWSE_RESULTS);
            }
            return references;
        } catch (Exception e) {
            throw ApiException.badGateway("浏览节点失败: " + e.getMessage());
        }
    }

    private Map<NodeId, VariableAttributes> readVariableAttributes(
        OpcUaClient client,
        List<NodeId> nodeIds,
        int timeout
    ) {
        Map<NodeId, VariableAttributes> result = new LinkedHashMap<>();
        if (nodeIds.isEmpty()) {
            return result;
        }
        List<ReadValueId> ids = new ArrayList<>();
        for (NodeId nodeId : nodeIds) {
            for (AttributeId attribute : List.of(
                AttributeId.DataType, AttributeId.ValueRank,
                AttributeId.AccessLevel, AttributeId.UserAccessLevel, AttributeId.Description
            )) {
                ids.add(new ReadValueId(nodeId, attribute.uid(), null, null));
            }
        }
        try {
            var response = Futures.await(
                client.read(0, TimestampsToReturn.Neither, ids),
                timeout,
                "读取节点属性"
            );
            DataValue[] values = response.getResults();
            for (int i = 0; i < nodeIds.size(); i++) {
                int base = i * 5;
                NodeId nodeId = nodeIds.get(i);
                result.put(nodeId, new VariableAttributes(
                    nodeIdValue(values[base]),
                    intValue(values[base + 1]),
                    ubyteInt(values[base + 2]),
                    ubyteInt(values[base + 3]),
                    textValue(values[base + 4], null)
                ));
            }
        } catch (Exception ignored) {
            for (NodeId nodeId : nodeIds) {
                result.putIfAbsent(nodeId, VariableAttributes.EMPTY);
            }
        }
        return result;
    }

    private Map<AttributeId, DataValue> readAttributes(
        OpcUaClient client,
        NodeId nodeId,
        List<AttributeId> attributes,
        int timeout
    ) {
        List<ReadValueId> ids = attributes.stream()
            .map(attribute -> new ReadValueId(nodeId, attribute.uid(), null, null))
            .toList();
        try {
            var response = Futures.await(client.read(0, TimestampsToReturn.Neither, ids), timeout, "读取节点属性");
            DataValue[] values = response.getResults();
            Map<AttributeId, DataValue> result = new LinkedHashMap<>();
            for (int i = 0; i < attributes.size(); i++) {
                result.put(attributes.get(i), values[i]);
            }
            return result;
        } catch (Exception e) {
            throw ApiException.badGateway("读取节点属性失败: " + e.getMessage());
        }
    }

    private String readDisplayName(OpcUaClient client, NodeId nodeId, int timeout) {
        try {
            DataValue value = Futures.await(
                client.read(0, TimestampsToReturn.Neither,
                    List.of(new ReadValueId(nodeId, AttributeId.DisplayName.uid(), null, null)))
                    .thenApply(response -> response.getResults()[0]),
                timeout,
                "读取显示名称"
            );
            return textValue(value, nodeId.toParseableString());
        } catch (Exception e) {
            return nodeId.toParseableString();
        }
    }

    private BrowseNodeView toView(
        ReferenceDescription reference,
        NodeId nodeId,
        VariableAttributes attributes,
        String parentPath
    ) {
        String displayName = displayName(reference);
        VariableAttributes attrs = attributes == null ? VariableAttributes.EMPTY : attributes;
        NodeId dataType = attrs.dataType();
        Integer accessLevel = attrs.userAccessLevel() != null ? attrs.userAccessLevel() : attrs.accessLevel();
        boolean writable = accessLevel != null && (accessLevel & 0x02) != 0;
        String dataTypeName = dataType == null
            ? null
            : com.opcua.web.domain.enums.TagDataType.displayName(dataType);
        return new BrowseNodeView(
            nodeId.toParseableString(),
            reference.getBrowseName() == null ? displayName : reference.getBrowseName().getName(),
            displayName,
            reference.getNodeClass() == null ? "Unknown" : reference.getNodeClass().name(),
            dataType == null ? null : dataType.toParseableString(),
            dataTypeName,
            dataType == null ? "AUTO" : com.opcua.web.domain.enums.TagDataType.fromNodeId(dataType).name(),
            attrs.valueRank(),
            attrs.valueRank() != null && attrs.valueRank() != -1,
            accessLevel,
            writable,
            attrs.description(),
            reference.getTypeDefinition() == null ? null : reference.getTypeDefinition().toParseableString(),
            reference.getNodeClass() == NodeClass.Object || reference.getNodeClass() == NodeClass.Variable,
            parentPath + "/" + displayName
        );
    }

    private NodeId targetNodeId(OpcUaClient client, ReferenceDescription reference) {
        if (reference == null || reference.getNodeId() == null) {
            return null;
        }
        try {
            return reference.getNodeId().toNodeIdOrThrow(client.getNamespaceTable());
        } catch (Exception e) {
            return null;
        }
    }

    private NodeId parseNodeId(String nodeIdText, NodeId defaultValue) {
        if (nodeIdText == null || nodeIdText.isBlank()) {
            if (defaultValue == null) {
                throw ApiException.badRequest("NodeId 不能为空");
            }
            return defaultValue;
        }
        try {
            NodeId nodeId = NodeId.parse(nodeIdText.trim());
            if (nodeId.isNull()) {
                throw ApiException.badRequest("NodeId 不能为空");
            }
            return nodeId;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("NodeId 格式无效: " + nodeIdText);
        }
    }

    private DeviceConfig loadDevice(String deviceId) {
        return store.read(data -> data.getDevices().stream()
            .filter(d -> d.getId().equals(deviceId))
            .findFirst()
            .orElseThrow(() -> ApiException.notFound("设备不存在: " + deviceId)));
    }

    private static String displayName(ReferenceDescription reference) {
        if (reference.getDisplayName() != null && reference.getDisplayName().getText() != null) {
            return reference.getDisplayName().getText();
        }
        if (reference.getBrowseName() != null && reference.getBrowseName().getName() != null) {
            return reference.getBrowseName().getName();
        }
        return reference.getNodeId() == null ? "?" : reference.getNodeId().toParseableString();
    }

    private static String textValue(DataValue dataValue, String fallback) {
        if (dataValue == null || dataValue.getValue() == null || dataValue.getValue().isNull()) {
            return fallback;
        }
        Object value = dataValue.getValue().getValue();
        if (value instanceof LocalizedText text) {
            return text.getText() == null ? fallback : text.getText();
        }
        return value == null ? fallback : String.valueOf(value);
    }

    private static NodeId nodeIdValue(DataValue dataValue) {
        if (dataValue == null || dataValue.getValue() == null || dataValue.getValue().isNull()) {
            return null;
        }
        Object value = dataValue.getValue().getValue();
        return value instanceof NodeId nodeId ? nodeId : null;
    }

    private static Integer intValue(DataValue dataValue) {
        if (dataValue == null || dataValue.getValue() == null || dataValue.getValue().isNull()) {
            return null;
        }
        Object value = dataValue.getValue().getValue();
        if (value instanceof Number number) return number.intValue();
        if (value instanceof UByte u) return u.intValue();
        if (value instanceof UInteger u) return u.intValue();
        return null;
    }

    private static Integer ubyteInt(DataValue dataValue) {
        if (dataValue == null || dataValue.getValue() == null || dataValue.getValue().isNull()) {
            return null;
        }
        Object value = dataValue.getValue().getValue();
        if (value instanceof UByte u) return u.intValue();
        if (value instanceof Number number) return number.intValue();
        return null;
    }

    private static QualifiedName qualifiedNameValue(DataValue dataValue) {
        if (dataValue == null || dataValue.getValue() == null || dataValue.getValue().isNull()) {
            return null;
        }
        Object value = dataValue.getValue().getValue();
        return value instanceof QualifiedName qn ? qn : null;
    }

    private static <E extends Enum<E>> E enumValue(DataValue dataValue, Class<E> type, E fallback) {
        if (dataValue == null || dataValue.getValue() == null || dataValue.getValue().isNull()) {
            return fallback;
        }
        Object value = dataValue.getValue().getValue();
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        return fallback;
    }

    private static NodeClass nodeClassValue(DataValue dataValue, NodeClass fallback) {
        if (dataValue == null || dataValue.getValue() == null || dataValue.getValue().isNull()) {
            return fallback;
        }
        Object value = dataValue.getValue().getValue();
        if (value instanceof NodeClass nodeClass) {
            return nodeClass;
        }
        int code = -1;
        if (value instanceof Number number) code = number.intValue();
        if (value instanceof UByte u) code = u.intValue();
        if (value instanceof UInteger u) code = u.intValue();
        NodeClass parsed = NodeClass.from(code);
        return parsed == null ? fallback : parsed;
    }

    private record VariableAttributes(
        NodeId dataType,
        Integer valueRank,
        Integer accessLevel,
        Integer userAccessLevel,
        String description
    ) {
        private static final VariableAttributes EMPTY =
            new VariableAttributes(null, null, null, null, null);
    }

    private record ScanItem(NodeId nodeId, String path, int depth) {
    }

    private record ReadTarget(ReferenceDescription reference, NodeId nodeId, String path) {
    }
}
