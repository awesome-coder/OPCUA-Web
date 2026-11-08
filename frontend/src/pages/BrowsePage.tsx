import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Empty,
  Form,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  Tree,
  Typography,
  message,
} from 'antd';
import type { DataNode } from 'antd/es/tree';
import type { ColumnsType } from 'antd/es/table';
import {
  ApartmentOutlined,
  CloudServerOutlined,
  CopyOutlined,
  DatabaseOutlined,
  FieldBinaryOutlined,
  FolderOpenOutlined,
  FolderOutlined,
  FunctionOutlined,
  NodeIndexOutlined,
  PlusOutlined,
  ReloadOutlined,
  ScanOutlined,
  SearchOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import type {
  BrowseNodeView,
  DeviceView,
  MetaView,
  NodeValueView,
  ScanResultView,
  TagRequest,
} from '../types';
import { api, errorText, formatValue } from '../api';
import PageHead from '../components/PageHead';
import TagFormModal from '../components/TagFormModal';
import { AccessTag, DataTypeTag, QualityTag } from '../components/StatusTags';

const { Text } = Typography;

const rootNodes: BrowseNodeView[] = [
  {
    nodeId: 'ns=0;i=85',
    browseName: 'Objects',
    displayName: '对象 Objects',
    nodeClass: 'Object',
    array: false,
    writable: false,
    hasChildren: true,
    path: 'Objects',
    description: '服务器实例地址空间根对象',
  },
  {
    nodeId: 'ns=0;i=84',
    browseName: 'Root',
    displayName: '根 Root',
    nodeClass: 'Object',
    array: false,
    writable: false,
    hasChildren: true,
    path: 'Root',
    description: '地址空间根节点',
  },
  {
    nodeId: 'ns=0;i=86',
    browseName: 'Types',
    displayName: '类型 Types',
    nodeClass: 'Object',
    array: false,
    writable: false,
    hasChildren: true,
    path: 'Types',
    description: '对象类型、变量类型和数据类型',
  },
];

function updateTreeData(list: DataNode[], key: React.Key, children: DataNode[], leaf: boolean): DataNode[] {
  return list.map((node) => {
    if (node.key === key) {
      return { ...node, children, isLeaf: leaf };
    }
    if (node.children) {
      return { ...node, children: updateTreeData(node.children, key, children, leaf) };
    }
    return node;
  });
}

function nodeTitle(node: BrowseNodeView) {
  const icon =
    node.nodeClass === 'Variable' ? (
      <FieldBinaryOutlined style={{ color: '#0284c7' }} />
    ) : node.nodeClass === 'Method' ? (
      <FunctionOutlined style={{ color: '#d97706' }} />
    ) : (
      <FolderOutlined style={{ color: '#7c3aed' }} />
    );
  return (
    <span>
      {icon} <span>{node.displayName || node.browseName}</span>
      <span className={`node-badge ${node.nodeClass === 'Variable' ? 'var' : 'obj'}`}>
        {node.nodeClass === 'Variable' ? node.dataTypeName || 'Var' : 'Obj'}
      </span>
      {node.array && <span className="node-badge var">[]</span>}
    </span>
  );
}

export default function BrowsePage() {
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const [devices, setDevices] = useState<DeviceView[]>([]);
  const [meta, setMeta] = useState<MetaView | null>(null);
  const [treeData, setTreeData] = useState<DataNode[]>([]);
  const [nodeIndex, setNodeIndex] = useState<Record<string, BrowseNodeView>>({});
  const [selected, setSelected] = useState<BrowseNodeView | null>(null);
  const [selectedKeys, setSelectedKeys] = useState<React.Key[]>([]);
  const [loading, setLoading] = useState(false);
  const [scanning, setScanning] = useState(false);
  const [scanResult, setScanResult] = useState<ScanResultView | null>(null);
  const [selectedScanKeys, setSelectedScanKeys] = useState<React.Key[]>([]);
  const [tagModalOpen, setTagModalOpen] = useState(false);
  const [initialNode, setInitialNode] = useState<BrowseNodeView | null>(null);
  const [nodeValue, setNodeValue] = useState<NodeValueView | null>(null);
  const [reading, setReading] = useState(false);
  const [scanForm] = Form.useForm();
  const loadedRef = useRef<Set<string>>(new Set());
  const selectedId = params.get('deviceId') || undefined;

  const loadDevices = async () => {
    setLoading(true);
    try {
      const [deviceResponse, metaResponse] = await Promise.all([
        api.get<DeviceView[]>('/devices'),
        meta ? Promise.resolve({ data: meta }) : api.get<MetaView>('/meta'),
      ]);
      setDevices(deviceResponse.data);
      setMeta(metaResponse.data);
      if (!deviceResponse.data.some((device) => device.id === selectedId)) {
        if (deviceResponse.data.length > 0) {
          setParams({ deviceId: deviceResponse.data[0].id }, { replace: true });
        }
      }
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadDevices();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    resetTree();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId]);

  const resetTree = () => {
    loadedRef.current = new Set();
    setScanResult(null);
    setSelectedScanKeys([]);
    setSelected(null);
    setSelectedKeys([]);
    setNodeValue(null);
    const index: Record<string, BrowseNodeView> = {};
    rootNodes.forEach((node) => {
      index[node.nodeId] = node;
    });
    setNodeIndex(index);
    setTreeData(
      rootNodes.map((node) => ({
        key: node.nodeId,
        title: nodeTitle(node),
        isLeaf: false,
        children: undefined,
      })),
    );
  };

  const loadNode = async (node: DataNode): Promise<void> => {
    if (!selectedId) return;
    const key = String(node.key);
    if (loadedRef.current.has(key)) return;
    try {
      const response = await api.get<{ nodes: BrowseNodeView[] }>(`/devices/${selectedId}/browse`, {
        params: { nodeId: key },
      });
      const children = response.data.nodes || [];
      const nextIndex = { ...nodeIndex };
      children.forEach((child) => {
        nextIndex[child.nodeId] = child;
      });
      setNodeIndex(nextIndex);
      loadedRef.current.add(key);
      const dataChildren: DataNode[] = children.map((child) => ({
        key: child.nodeId,
        title: nodeTitle(child),
        isLeaf: !child.hasChildren,
      }));
      setTreeData((origin) => updateTreeData(origin, node.key, dataChildren, dataChildren.length === 0));
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const readNode = async () => {
    if (!selectedId || !selected) return;
    setReading(true);
    try {
      const response = await api.post(`/devices/${selectedId}/read`, {
        tagIds: [],
        nodeIds: [selected.nodeId],
      });
      const value = response.data.nodes?.[0] as NodeValueView | undefined;
      if (value) {
        setNodeValue(value);
        if (value.error) message.error(value.error);
      }
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setReading(false);
    }
  };

  const runScan = async () => {
    if (!selectedId) return;
    try {
      const values = await scanForm.validateFields();
      setScanning(true);
      setScanResult(null);
      setSelectedScanKeys([]);
      const response = await api.post<ScanResultView>(
        `/devices/${selectedId}/scan`,
        {
          nodeId: selected?.nodeId || 'ns=0;i=85',
          maxDepth: values.maxDepth,
          maxNodes: values.maxNodes,
          includeProperties: values.includeProperties,
        },
        { timeout: 180_000 },
      );
      setScanResult(response.data);
      setSelectedScanKeys(response.data.tags.slice(0, 20).map((node) => node.nodeId));
      if (response.data.tags.length === 0) message.warning('没有扫描到变量节点');
      else message.success(`扫描到 ${response.data.tags.length} 个变量`);
    } catch (error: any) {
      if (error?.errorFields) return;
      message.error(errorText(error));
    } finally {
      setScanning(false);
    }
  };

  const addSelectedScanTags = async () => {
    if (!selectedId || !scanResult) return;
    const selectedNodes = scanResult.tags.filter((node) => selectedScanKeys.includes(node.nodeId));
    if (selectedNodes.length === 0) {
      message.warning('请先勾选要添加的变量');
      return;
    }
    const tags: TagRequest[] = selectedNodes.map((node) => ({
      deviceId: selectedId,
      name: node.displayName || node.browseName || node.nodeId,
      nodeId: node.nodeId,
      dataType: node.dataTypeCode || 'AUTO',
      array: node.array,
      accessMode: node.writable ? 'READ_WRITE' : 'READ_ONLY',
      unit: null,
      description: node.description || node.path,
      scale: 1,
      offset: 0,
      deadband: 0,
      samplingIntervalMs: 0,
      queueSize: 1,
      enumValues: null,
      bacnetObjectType: null,
      bacnetInstance: null,
      bacnetProperty: 'present-value',
      covIncrement: null,
      enabled: true,
    }));
    try {
      const response = await api.post(`/devices/${selectedId}/tags/batch`, { tags });
      const body = response.data;
      message.success(`已创建 ${body.created?.length || 0} 个点位`);
      if (body.errors?.length) message.warning(`${body.errors.length} 个点位创建失败`);
      setSelectedScanKeys([]);
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const scanColumns: ColumnsType<BrowseNodeView> = [
    {
      title: '变量',
      dataIndex: 'displayName',
      key: 'displayName',
      width: 190,
      render: (value, node) => (
        <div>
          <div style={{ fontWeight: 600 }}>{value}</div>
          <div className="tiny-muted" style={{ marginTop: 2 }}>
            {node.browseName}
          </div>
        </div>
      ),
    },
    {
      title: 'NodeId',
      dataIndex: 'nodeId',
      key: 'nodeId',
      width: 230,
      render: (value) => <span className="node-id">{value}</span>,
    },
    {
      title: '类型',
      key: 'type',
      width: 120,
      render: (_, node) => <DataTypeTag type={(node.dataTypeCode as any) || 'AUTO'} array={node.array} />,
    },
    {
      title: '访问',
      key: 'access',
      width: 80,
      render: (_, node) => <AccessTag mode={node.writable ? 'READ_WRITE' : 'READ_ONLY'} />,
    },
    {
      title: '路径',
      dataIndex: 'path',
      key: 'path',
      ellipsis: true,
    },
  ];

  const selectedDevice = devices.find((device) => device.id === selectedId);
  const scanSelected = useMemo(
    () => scanResult?.tags.filter((node) => selectedScanKeys.includes(node.nodeId)) || [],
    [scanResult, selectedScanKeys],
  );

  return (
    <div className="page-stack">
      <PageHead
        title="地址空间浏览"
        description="按需浏览服务器层级节点，查看变量类型、访问权限和值，并从任意对象节点递归扫描变量后批量创建点位。"
        extra={
          <Space>
            <Select
              style={{ width: 280 }}
              placeholder="选择设备"
              value={selectedId}
              showSearch
              optionFilterProp="label"
              onChange={(value) => setParams({ deviceId: value })}
              options={devices.map((device) => ({ value: device.id, label: device.name }))}
            />
            <Button
              icon={<ReloadOutlined />}
              onClick={() => {
                resetTree();
                message.success('已重置节点树');
              }}
              disabled={!selectedId}
            >
              重置
            </Button>
          </Space>
        }
      />

      {!selectedId ? (
        <Card variant="borderless">
          <Empty description="请先选择设备">
            <Button type="primary" onClick={() => navigate('/devices')}>
              前往设备管理
            </Button>
          </Empty>
        </Card>
      ) : (
        <div className="browse-grid">
          <Card
            variant="borderless"
            title={
              <Space>
                <ApartmentOutlined />
                <span>节点树</span>
                {selectedDevice && <Tag>{selectedDevice.endpointUrl}</Tag>}
              </Space>
            }
            loading={loading}
            className="tree-panel"
          >
            <Tree
              className="browse-tree"
              showLine
              blockNode
              treeData={treeData}
              loadData={loadNode}
              selectedKeys={selectedKeys}
              onSelect={(keys, info) => {
                setSelectedKeys(keys);
                const key = String(keys[0] || '');
                setSelected(nodeIndex[key] || null);
                if (!keys.length) setSelected(null);
              }}
            />
            <div className="tiny-muted" style={{ marginTop: 14, lineHeight: 1.7 }}>
              点击对象节点可扫描其子树；点击变量节点可读取当前值或添加为点位。
            </div>
          </Card>

          <div className="page-stack">
            <Card
              variant="borderless"
              title={
                <Space>
                  <NodeIndexOutlined />
                  <span>节点详情</span>
                </Space>
              }
            >
              {selected ? (
                <>
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
                    <div>
                      <Space wrap>
                        <span style={{ fontSize: 18, fontWeight: 700 }}>{selected.displayName}</span>
                        <Tag color={selected.nodeClass === 'Variable' ? 'blue' : 'purple'}>
                          {selected.nodeClass}
                        </Tag>
                        {selected.array && <Tag color="geekblue">数组</Tag>}
                      </Space>
                      <div style={{ marginTop: 8 }}>
                        <span className="node-id">{selected.nodeId}</span>
                      </div>
                    </div>
                    <Space wrap>
                      <Button
                        size="small"
                        icon={<CopyOutlined />}
                        onClick={() => {
                          navigator.clipboard.writeText(selected.nodeId);
                          message.success('NodeId 已复制');
                        }}
                      >
                        复制
                      </Button>
                      {selected.nodeClass === 'Variable' && (
                        <>
                          <Button
                            size="small"
                            icon={<ThunderboltOutlined />}
                            loading={reading}
                            onClick={readNode}
                          >
                            读取值
                          </Button>
                          <Button
                            type="primary"
                            size="small"
                            icon={<PlusOutlined />}
                            onClick={() => {
                              setInitialNode(selected);
                              setTagModalOpen(true);
                            }}
                          >
                            添加为点位
                          </Button>
                        </>
                      )}
                      <Button
                        size="small"
                        icon={<ScanOutlined />}
                        onClick={() => {
                          scanForm.setFieldsValue({
                            maxDepth: 4,
                            maxNodes: 500,
                            includeProperties: false,
                          });
                          runScan();
                        }}
                      >
                        扫描子树
                      </Button>
                    </Space>
                  </div>
                  <Descriptions column={2} size="small" bordered style={{ marginTop: 18 }}>
                    <Descriptions.Item label="BrowseName">{selected.browseName}</Descriptions.Item>
                    <Descriptions.Item label="节点类">{selected.nodeClass}</Descriptions.Item>
                    <Descriptions.Item label="数据类型">
                      {selected.dataTypeName || '—'}
                      {selected.dataType ? (
                        <span className="tiny-muted"> ({selected.dataType})</span>
                      ) : null}
                    </Descriptions.Item>
                    <Descriptions.Item label="ValueRank">
                      {selected.valueRank ?? '—'}
                    </Descriptions.Item>
                    <Descriptions.Item label="访问权限">
                      <AccessTag mode={selected.writable ? 'READ_WRITE' : 'READ_ONLY'} />
                    </Descriptions.Item>
                    <Descriptions.Item label="类型定义">
                      <span className="mono">{selected.typeDefinition || '—'}</span>
                    </Descriptions.Item>
                    <Descriptions.Item label="描述" span={2}>
                      {selected.description || '—'}
                    </Descriptions.Item>
                    <Descriptions.Item label="路径" span={2}>
                      {selected.path}
                    </Descriptions.Item>
                  </Descriptions>
                </>
              ) : (
                <div className="detail-empty">
                  <FolderOpenOutlined style={{ fontSize: 42, color: '#cbd5e1' }} />
                  <div>从左侧节点树选择一个节点</div>
                  <div>对象节点可以扫描；变量节点可以读取和添加点位</div>
                </div>
              )}
            </Card>

            <Card
              variant="borderless"
              title={
                <Space>
                  <SearchOutlined />
                  <span>变量扫描</span>
                </Space>
              }
              extra={
                <Space>
                  <span className="tiny-muted">已选 {scanSelected.length}</span>
                  <Button
                    type="primary"
                    size="small"
                    icon={<DatabaseOutlined />}
                    disabled={scanSelected.length === 0}
                    onClick={addSelectedScanTags}
                  >
                    批量添加点位
                  </Button>
                </Space>
              }
            >
              <Form
                form={scanForm}
                layout="inline"
                initialValues={{ maxDepth: 4, maxNodes: 500, includeProperties: false }}
                style={{ marginBottom: 14, rowGap: 10 }}
              >
                <Form.Item name="maxDepth" label="扫描深度">
                  <InputNumber min={1} max={15} style={{ width: 80 }} />
                </Form.Item>
                <Form.Item name="maxNodes" label="最大变量数">
                  <InputNumber min={1} max={10000} style={{ width: 100 }} />
                </Form.Item>
                <Form.Item name="includeProperties" label="包含属性" valuePropName="checked">
                  <Switch size="small" />
                </Form.Item>
                <Form.Item>
                  <Button type="primary" icon={<ScanOutlined />} loading={scanning} onClick={runScan}>
                    扫描 {selected ? selected.displayName : 'Objects'}
                  </Button>
                </Form.Item>
              </Form>
              {scanResult && (
                <div className="scan-summary" style={{ marginBottom: 12 }}>
                  <span>
                    对象 <b>{scanResult.scannedObjects}</b>
                  </span>
                  <span>
                    变量 <b>{scanResult.scannedVariables}</b>
                  </span>
                  <span>
                    耗时 <b>{scanResult.elapsedMs}ms</b>
                  </span>
                  {scanResult.truncated && <Tag color="warning">结果已截断</Tag>}
                  {scanResult.warnings.map((warning) => (
                    <Tag color="warning" key={warning}>
                      {warning}
                    </Tag>
                  ))}
                </div>
              )}
              <Table<BrowseNodeView>
                rowKey="nodeId"
                size="small"
                loading={scanning}
                dataSource={scanResult?.tags || []}
                columns={scanColumns}
                pagination={{ pageSize: 8, showSizeChanger: false }}
                rowSelection={{
                  selectedRowKeys: selectedScanKeys,
                  onChange: (keys) => setSelectedScanKeys(keys),
                }}
                scroll={{ x: 900 }}
                locale={{ emptyText: '点击“扫描”从当前节点递归发现变量' }}
              />
            </Card>
          </div>
        </div>
      )}

      <TagFormModal
        open={tagModalOpen}
        deviceId={selectedId}
        initialNode={initialNode}
        meta={meta}
        onClose={() => setTagModalOpen(false)}
        onSaved={() => {
          setTagModalOpen(false);
          message.success('点位已创建，可在点位配置中查看');
        }}
      />

      <Modal
        title={nodeValue ? `节点读取 · ${nodeValue.nodeId}` : '节点读取'}
        open={!!nodeValue}
        onCancel={() => setNodeValue(null)}
        footer={<Button onClick={() => setNodeValue(null)}>关闭</Button>}
        width={600}
      >
        {nodeValue && (
          <>
            <div style={{ marginBottom: 14 }}>
              <QualityTag quality={nodeValue.quality} />{' '}
              <span className="tiny-muted">{nodeValue.statusCodeName}</span>
            </div>
            <div
              style={{
                background: '#0f172a',
                color: '#e2e8f0',
                padding: 14,
                borderRadius: 10,
                fontFamily: 'JetBrains Mono, Consolas, monospace',
                fontSize: 13,
                wordBreak: 'break-all',
                whiteSpace: 'pre-wrap',
              }}
            >
              {formatValue(nodeValue.value)}
            </div>
            <Descriptions style={{ marginTop: 16 }} column={1} size="small">
              <Descriptions.Item label="数据类型">{nodeValue.dataType}</Descriptions.Item>
              <Descriptions.Item label="源时间">{nodeValue.sourceTime || '—'}</Descriptions.Item>
              <Descriptions.Item label="服务器时间">{nodeValue.serverTime || '—'}</Descriptions.Item>
              {nodeValue.error && (
                <Descriptions.Item label="错误">
                  <Text type="danger">{nodeValue.error}</Text>
                </Descriptions.Item>
              )}
            </Descriptions>
          </>
        )}
      </Modal>
    </div>
  );
}
