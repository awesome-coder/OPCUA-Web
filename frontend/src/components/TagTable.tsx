import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Divider,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  Upload,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  CloudDownloadOutlined,
  CloudUploadOutlined,
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  EyeOutlined,
  FileExcelOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  UploadOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import type {
  DeviceView,
  ExcelImportResult,
  MetaView,
  TagValueView,
  TagView,
  WriteResult,
} from '../types';
import { api, download, errorText, formatValue } from '../api';
import { AccessTag, DataTypeTag, QualityTag } from './StatusTags';
import TagFormModal from './TagFormModal';

const bacnetAbbreviations: Record<string, string> = {
  ANALOG_INPUT: 'AI',
  ANALOG_OUTPUT: 'AO',
  ANALOG_VALUE: 'AV',
  BINARY_INPUT: 'BI',
  BINARY_OUTPUT: 'BO',
  BINARY_VALUE: 'BV',
  MULTI_STATE_INPUT: 'MSI',
  MULTI_STATE_OUTPUT: 'MSO',
  MULTI_STATE_VALUE: 'MSV',
  CHARACTER_STRING_VALUE: 'CSV',
};

function renderTagValue(tag: TagView, raw: unknown): string {
  if (
    tag.dataType === 'DATETIME' &&
    typeof raw === 'string' &&
    !Number.isNaN(Date.parse(raw))
  ) {
    return new Date(raw).toLocaleString('zh-CN', { hour12: false });
  }
  return formatValue(raw);
}

export default function TagTable({
  deviceId,
  showDeviceColumn = false,
  allowImport = true,
}: {
  deviceId?: string;
  showDeviceColumn?: boolean;
  allowImport?: boolean;
}) {
  const [tags, setTags] = useState<TagView[]>([]);
  const [devices, setDevices] = useState<DeviceView[]>([]);
  const [meta, setMeta] = useState<MetaView | null>(null);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<TagView | null>(null);
  const [readValue, setReadValue] = useState<TagValueView | null>(null);
  const [readLoading, setReadLoading] = useState<string | null>(null);
  const [readingAll, setReadingAll] = useState(false);
  const [writeTag, setWriteTag] = useState<TagView | null>(null);
  const [writeForm] = Form.useForm();
  const [writing, setWriting] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [importFile, setImportFile] = useState<File | null>(null);
  const [importOverwrite, setImportOverwrite] = useState(true);
  const [importing, setImporting] = useState(false);
  const [importResult, setImportResult] = useState<ExcelImportResult | null>(null);
  const [values, setValues] = useState<Record<string, TagValueView>>({});

  const load = async () => {
    setLoading(true);
    try {
      const [tagResponse, deviceResponse, metaResponse] = await Promise.all([
        api.get<TagView[]>('/tags', { params: deviceId ? { deviceId } : undefined }),
        api.get<DeviceView[]>('/devices'),
        meta ? Promise.resolve({ data: meta }) : api.get<MetaView>('/meta'),
      ]);
      setTags(tagResponse.data);
      setDevices(deviceResponse.data);
      setMeta(metaResponse.data);
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [deviceId]);

  const filtered = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    if (!keyword) return tags;
    return tags.filter((tag) =>
      [tag.name, tag.nodeId, tag.unit, tag.description, tag.deviceName, tag.bacnetObjectType]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(keyword)),
    );
  }, [tags, search]);

  const readTag = async (tag: TagView) => {
    setReadLoading(tag.id);
    try {
      const response = await api.post(`/devices/${tag.deviceId}/read`, {
        tagIds: [tag.id],
        nodeIds: [],
      });
      const result = response.data.tags?.[0] as TagValueView | undefined;
      if (result) {
        setReadValue(result);
        setValues((current) => ({ ...current, [tag.id]: result }));
        if (result.quality === 'GOOD') message.success(`${tag.name} 读取成功`);
        else message.warning(`${tag.name} 质量: ${result.quality}`);
      }
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setReadLoading(null);
    }
  };

  const readAll = async () => {
    const readable = filtered.filter((tag) => tag.accessMode !== 'WRITE_ONLY');
    if (readable.length === 0) {
      message.warning('没有可读取的点位');
      return;
    }
    try {
      setReadingAll(true);
      const groups = new Map<string, TagView[]>();
      readable.forEach((tag) => {
        const list = groups.get(tag.deviceId) || [];
        list.push(tag);
        groups.set(tag.deviceId, list);
      });
      const nextValues: Record<string, TagValueView> = { ...values };
      let count = 0;
      for (const [tagDeviceId, group] of groups) {
        const response = await api.post(`/devices/${tagDeviceId}/read`, {
          tagIds: group.map((tag) => tag.id),
          nodeIds: [],
        });
        (response.data.tags as TagValueView[]).forEach((value) => {
          nextValues[value.tagId] = value;
          count++;
        });
      }
      setValues(nextValues);
      message.success(`已读取 ${count} 个点位`);
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setReadingAll(false);
    }
  };

  const deleteTag = async (tag: TagView) => {
    try {
      await api.delete(`/tags/${tag.id}`);
      message.success('点位已删除');
      load();
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const openWrite = (tag: TagView) => {
    setWriteTag(tag);
    const current = values[tag.id]?.value;
    writeForm.setFieldsValue({
      value:
        current === undefined || current === null
          ? ''
          : typeof current === 'object'
            ? JSON.stringify(current)
            : String(current),
    });
  };

  const submitWrite = async () => {
    if (!writeTag) return;
    try {
      const values = await writeForm.validateFields();
      setWriting(true);
      const response = await api.post<WriteResult>(`/devices/${writeTag.deviceId}/write`, {
        tagId: writeTag.id,
        value: values.value,
      });
      if (response.data.success) {
        message.success(`写入成功 · ${response.data.statusCodeName}`);
        setWriteTag(null);
        readTag(writeTag);
      } else {
        message.error(`写入被拒绝 · ${response.data.statusCodeName}`);
      }
    } catch (error: any) {
      if (error?.errorFields) return;
      message.error(errorText(error));
    } finally {
      setWriting(false);
    }
  };

  const runImport = async () => {
    if (!importFile) {
      message.warning('请选择 Excel 文件');
      return;
    }
    try {
      setImporting(true);
      setImportResult(null);
      const formData = new FormData();
      formData.append('file', importFile);
      const response = await api.post<ExcelImportResult>('/excel/import', formData, {
        params: { overwrite: importOverwrite },
        headers: { 'Content-Type': 'multipart/form-data' },
        timeout: 120_000,
      });
      setImportResult(response.data);
      if (response.data.errorCount > 0) message.warning(response.data.message);
      else message.success(response.data.message);
      load();
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setImporting(false);
    }
  };

  const columns: ColumnsType<TagView> = [
    {
      title: '点位',
      dataIndex: 'name',
      key: 'name',
      fixed: 'left',
      width: 180,
      render: (_, tag) => (
        <div>
          <div style={{ fontWeight: 650, display: 'flex', alignItems: 'center', gap: 7 }}>
            <span
              style={{
                width: 7,
                height: 7,
                borderRadius: '50%',
                background: tag.enabled ? '#22c55e' : '#cbd5e1',
                display: 'inline-block',
              }}
            />
            {tag.name}
          </div>
          {tag.description ? (
            <div className="tiny-muted" style={{ marginTop: 3, maxWidth: 190 }}>
              {tag.description}
            </div>
          ) : null}
        </div>
      ),
    },
    ...(showDeviceColumn
      ? [
          {
            title: '设备',
            dataIndex: 'deviceName',
            key: 'deviceName',
            width: 140,
            ellipsis: true,
          } as ColumnsType<TagView>[number],
        ]
      : []),
    {
      title: 'NodeId',
      dataIndex: 'nodeId',
      key: 'nodeId',
      width: 170,
      render: (value: string) => (
        <Tooltip title={value}>
          <span className="node-id">{value.length > 30 ? `${value.slice(0, 28)}…` : value}</span>
        </Tooltip>
      ),
    },
    {
      title: '类型',
      dataIndex: 'dataType',
      key: 'dataType',
      width: 100,
      render: (_, tag) => <DataTypeTag type={tag.dataType} array={tag.array} />,
    },
    {
      title: '访问',
      dataIndex: 'accessMode',
      key: 'accessMode',
      width: 70,
      render: (value) => <AccessTag mode={value} />,
    },
    {
      title: '最近值',
      key: 'lastValue',
      width: 145,
      render: (_, tag) => {
        const value = values[tag.id];
        if (!value) return <span className="tiny-muted">未读取</span>;
        return (
          <Space size={6} wrap>
            <span className="mono" style={{ fontWeight: 600 }}>
              {renderTagValue(tag, value.value)}
            </span>
            {value.unit ? <span className="tiny-muted">{value.unit}</span> : null}
            <Tooltip title={`质量 ${value.quality} · ${value.statusCodeName}`}>
              <span
                style={{
                  width: 7,
                  height: 7,
                  borderRadius: '50%',
                  display: 'inline-block',
                  background:
                    value.quality === 'GOOD'
                      ? '#22c55e'
                      : value.quality === 'UNCERTAIN'
                        ? '#f59e0b'
                        : '#ef4444',
                }}
              />
            </Tooltip>
          </Space>
        );
      },
    },
    {
      title: '换算',
      key: 'conversion',
      width: 80,
      render: (_, tag) =>
        tag.scale === 1 && tag.offset === 0 ? (
          <span className="tiny-muted">无</span>
        ) : (
          <span className="mono" style={{ fontSize: 11 }}>
            ×{tag.scale} {tag.offset >= 0 ? '+' : ''}
            {tag.offset}
          </span>
        ),
    },
    {
      title: 'BACnet',
      key: 'bacnet',
      width: 125,
      render: (_, tag) => {
        if (!tag.bacnetObjectType) return <span className="tiny-muted">AUTO</span>;
        const abbreviation = bacnetAbbreviations[tag.bacnetObjectType] || tag.bacnetObjectType;
        return (
          <Tooltip title={`${tag.bacnetObjectType}${tag.bacnetInstance !== undefined ? `,${tag.bacnetInstance}` : ''}`}>
            <Tag color="cyan" className="mono" style={{ fontSize: 10 }}>
              {abbreviation}
              {tag.bacnetInstance !== undefined ? `,${tag.bacnetInstance}` : ''}
            </Tag>
          </Tooltip>
        );
      },
    },
    {
      title: '操作',
      key: 'actions',
      fixed: 'right',
      width: 155,
      render: (_, tag) => (
        <Space size={2}>
          <Tooltip title="读取当前值">
            <Button
              type="text"
              size="small"
              icon={<EyeOutlined />}
              loading={readLoading === tag.id}
              onClick={() => readTag(tag)}
            />
          </Tooltip>
          {tag.writable && (
            <Tooltip title="写入值">
              <Button
                type="text"
                size="small"
                icon={<ThunderboltOutlined />}
                onClick={() => openWrite(tag)}
              />
            </Tooltip>
          )}
          <Tooltip title="编辑">
            <Button
              type="text"
              size="small"
              icon={<EditOutlined />}
              onClick={() => {
                setEditing(tag);
                setFormOpen(true);
              }}
            />
          </Tooltip>
          <Popconfirm
            title="删除点位"
            description={`确定删除 ${tag.name}？`}
            okText="删除"
            okButtonProps={{ danger: true }}
            cancelText="取消"
            onConfirm={() => deleteTag(tag)}
          >
            <Button type="text" danger size="small" icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div className="page-stack">
      <Card className="table-card" variant="borderless">
        <div className="toolbar" style={{ marginBottom: 16 }}>
          <div className="toolbar-left">
            <Input
              allowClear
              prefix={<SearchOutlined />}
              placeholder="搜索名称、NodeId、单位..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              style={{ width: 280 }}
            />
            <Button icon={<ReloadOutlined />} onClick={load} loading={loading}>
              刷新
            </Button>
            <Button icon={<EyeOutlined />} onClick={readAll} loading={readingAll}>
              读取全部
            </Button>
            <span className="tiny-muted">共 {filtered.length} 个点位</span>
          </div>
          <div className="toolbar-right">
            <Button
              icon={<FileExcelOutlined />}
              onClick={() =>
                download(
                  deviceId ? `/excel/export?deviceId=${encodeURIComponent(deviceId)}` : '/excel/export',
                  'opcua-config.xlsx',
                ).catch((error) => message.error(errorText(error)))
              }
            >
              导出 Excel
            </Button>
            <Button
              icon={<DownloadOutlined />}
              onClick={() =>
                download('/excel/template', 'opcua-config-template.xlsx').catch((error) =>
                  message.error(errorText(error)),
                )
              }
            >
              模板
            </Button>
            {allowImport && (
              <Button icon={<CloudUploadOutlined />} onClick={() => setImportOpen(true)}>
                导入 Excel
              </Button>
            )}
            <Button
              type="primary"
              icon={<PlusOutlined />}
              disabled={!deviceId}
              onClick={() => {
                setEditing(null);
                setFormOpen(true);
              }}
            >
              新增点位
            </Button>
          </div>
        </div>
        {!deviceId && (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 14 }}
            message="当前展示所有设备的点位。新增点位前请先选择设备。"
          />
        )}
        <Table<TagView>
          rowKey="id"
          columns={columns}
          dataSource={filtered}
          loading={loading}
          size="middle"
          scroll={{ x: showDeviceColumn ? 1165 : 1025 }}
          pagination={{ pageSize: 12, showSizeChanger: true, showTotal: (total) => `共 ${total} 条` }}
          locale={{ emptyText: '尚未配置点位，点击“新增点位”开始。' }}
        />
      </Card>

      <TagFormModal
        open={formOpen}
        deviceId={deviceId || editing?.deviceId}
        editing={editing}
        meta={meta}
        onClose={() => setFormOpen(false)}
        onSaved={load}
      />

      <Modal
        title={readValue ? `读取结果 · ${readValue.tagName}` : '读取结果'}
        open={!!readValue}
        onCancel={() => setReadValue(null)}
        footer={<Button onClick={() => setReadValue(null)}>关闭</Button>}
        width={640}
      >
        {readValue && (
          <>
            <Descriptions column={2} size="small" bordered>
              <Descriptions.Item label="值" span={2}>
                <span className="mono" style={{ fontWeight: 650 }}>
                  {formatValue(readValue.value)}
                </span>
                {readValue.unit ? <span className="tiny-muted"> {readValue.unit}</span> : null}
              </Descriptions.Item>
              <Descriptions.Item label="质量">
                <QualityTag quality={readValue.quality} />
              </Descriptions.Item>
              <Descriptions.Item label="状态码">{readValue.statusCodeName}</Descriptions.Item>
              <Descriptions.Item label="数据类型">{readValue.dataType}</Descriptions.Item>
              <Descriptions.Item label="缩放">
                {readValue.scaled ? '已应用工程换算' : '未启用'}
              </Descriptions.Item>
              <Descriptions.Item label="原始值" span={2}>
                <span className="mono">{formatValue(readValue.rawValue)}</span>
              </Descriptions.Item>
              <Descriptions.Item label="源时间">{readValue.sourceTime || '—'}</Descriptions.Item>
              <Descriptions.Item label="服务器时间">{readValue.serverTime || '—'}</Descriptions.Item>
              <Descriptions.Item label="NodeId" span={2}>
                <span className="node-id">{readValue.nodeId}</span>
              </Descriptions.Item>
            </Descriptions>
            {readValue.error && (
              <Alert type="error" showIcon message={readValue.error} style={{ marginTop: 12 }} />
            )}
          </>
        )}
      </Modal>

      <Modal
        title={writeTag ? `写入点位 · ${writeTag.name}` : '写入点位'}
        open={!!writeTag}
        onCancel={() => setWriteTag(null)}
        onOk={submitWrite}
        okText="写入"
        confirmLoading={writing}
        width={560}
      >
        {writeTag && (
          <>
            <Alert
              type="warning"
              showIcon
              style={{ marginBottom: 16 }}
              message="写入会直接修改 OPC UA 服务器数据"
              description="该操作受“访问方式”配置限制。数组请使用 JSON 数组，例如 [1,2,3]。"
            />
            <Space size={6} wrap style={{ marginBottom: 14 }}>
              <DataTypeTag type={writeTag.dataType} array={writeTag.array} />
              <span className="node-id">{writeTag.nodeId}</span>
            </Space>
            <Form form={writeForm} layout="vertical">
              <Form.Item
                name="value"
                label="写入值"
                rules={[{ required: true, message: '请输入写入值' }]}
              >
                <Input.TextArea
                  rows={writeTag.array ? 4 : 2}
                  className="mono"
                  placeholder={writeTag.array ? '[1, 2, 3]' : '输入值'}
                />
              </Form.Item>
            </Form>
          </>
        )}
      </Modal>

      <Modal
        title="从 Excel 导入配置"
        open={importOpen}
        onCancel={() => {
          setImportOpen(false);
          setImportResult(null);
        }}
        width={760}
        footer={
          <Space>
            <Button
              onClick={() => {
                setImportOpen(false);
                setImportResult(null);
              }}
            >
              关闭
            </Button>
            <Button type="primary" onClick={runImport} loading={importing} disabled={!importFile}>
              开始导入
            </Button>
          </Space>
        }
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="Excel 按“设备”和“点位”两个工作表组织"
          description={
            <span>
              设备按名称匹配；点位按“设备 + 点位名称”匹配。建议先下载模板。密码不会导出；导入时留空表示保留现有密码。
              <Button
                type="link"
                size="small"
                style={{ paddingLeft: 4 }}
                onClick={() =>
                  download('/excel/template', 'opcua-config-template.xlsx').catch((error) =>
                    message.error(errorText(error)),
                  )
                }
              >
                下载模板
              </Button>
            </span>
          }
        />
        <Upload.Dragger
          accept=".xlsx,.xls"
          maxCount={1}
          beforeUpload={(file) => {
            setImportFile(file);
            return false;
          }}
          onRemove={() => setImportFile(null)}
          fileList={
            importFile
              ? [
                  {
                    uid: 'excel',
                    name: importFile.name,
                    status: 'done',
                  } as any,
                ]
              : []
          }
        >
          <p className="ant-upload-drag-icon">
            <UploadOutlined />
          </p>
          <p className="ant-upload-text">点击或拖拽 Excel 文件到此处</p>
          <p className="ant-upload-hint">支持 .xlsx / .xls，最大 20MB</p>
        </Upload.Dragger>
        <div style={{ marginTop: 16, display: 'flex', alignItems: 'center', gap: 10 }}>
          <Switch checked={importOverwrite} onChange={setImportOverwrite} />
          <span style={{ fontSize: 12 }}>覆盖同名设备 / 点名（关闭时跳过已存在项）</span>
        </div>
        {importResult && (
          <div style={{ marginTop: 18 }}>
            <Divider style={{ margin: '0 0 14px' }} />
            <Alert
              type={importResult.errorCount > 0 ? 'warning' : 'success'}
              showIcon
              message={importResult.message}
              description={
                <Space size={16} wrap>
                  <span>设备 +{importResult.devicesCreated} / ~{importResult.devicesUpdated}</span>
                  <span>点位 +{importResult.tagsCreated} / ~{importResult.tagsUpdated}</span>
                  <span>跳过 {importResult.tagsSkipped}</span>
                  <span>错误 {importResult.errorCount}</span>
                </Space>
              }
            />
            {importResult.errors.length > 0 && (
              <Table
                style={{ marginTop: 12 }}
                size="small"
                pagination={false}
                scroll={{ y: 220 }}
                rowKey={(row) => `${row.sheet}-${row.row}-${row.column}-${row.message}`}
                dataSource={importResult.errors}
                columns={[
                  { title: '工作表', dataIndex: 'sheet', width: 70 },
                  { title: '行', dataIndex: 'row', width: 60 },
                  { title: '列', dataIndex: 'column', width: 90 },
                  { title: '错误', dataIndex: 'message' },
                ]}
              />
            )}
          </div>
        )}
      </Modal>
    </div>
  );
}
