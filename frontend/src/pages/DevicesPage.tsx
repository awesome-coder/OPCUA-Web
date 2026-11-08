import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Input,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ApiOutlined,
  CloudServerOutlined,
  DeleteOutlined,
  DisconnectOutlined,
  EditOutlined,
  LinkOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import type { DeviceView, MetaView, SecurityPolicy } from '../types';
import { api, errorText } from '../api';
import PageHead from '../components/PageHead';
import DeviceFormDrawer from '../components/DeviceFormDrawer';
import {
  ConnectionTag,
  SecurityModeTag,
  SecurityTag,
  authLabel,
} from '../components/StatusTags';

export default function DevicesPage() {
  const navigate = useNavigate();
  const [devices, setDevices] = useState<DeviceView[]>([]);
  const [meta, setMeta] = useState<MetaView | null>(null);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');
  const [securityFilter, setSecurityFilter] = useState<string | undefined>();
  const [statusFilter, setStatusFilter] = useState<string | undefined>();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<DeviceView | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    try {
      const [deviceResponse, metaResponse] = await Promise.all([
        api.get<DeviceView[]>('/devices'),
        meta ? Promise.resolve({ data: meta }) : api.get<MetaView>('/meta'),
      ]);
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
  }, []);

  const filtered = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    return devices.filter((device) => {
      if (
        keyword &&
        ![device.name, device.endpointUrl, device.description, device.username]
          .filter(Boolean)
          .some((value) => String(value).toLowerCase().includes(keyword))
      ) {
        return false;
      }
      if (securityFilter === 'secure' && device.securityPolicy === 'NONE') return false;
      if (securityFilter === 'none' && device.securityPolicy !== 'NONE') return false;
      if (statusFilter === 'online' && !device.connected) return false;
      if (statusFilter === 'offline' && device.connected) return false;
      if (statusFilter === 'error' && device.connectionState !== 'ERROR') return false;
      return true;
    });
  }, [devices, search, securityFilter, statusFilter]);

  const connect = async (device: DeviceView) => {
    setBusyId(device.id);
    try {
      await api.post(`/devices/${device.id}/connect`);
      message.success(`${device.name} 已连接`);
      load();
    } catch (error) {
      message.error(errorText(error));
      load();
    } finally {
      setBusyId(null);
    }
  };

  const disconnect = async (device: DeviceView) => {
    setBusyId(device.id);
    try {
      await api.post(`/devices/${device.id}/disconnect`);
      message.success(`${device.name} 已断开`);
      load();
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setBusyId(null);
    }
  };

  const remove = async (device: DeviceView) => {
    try {
      await api.delete(`/devices/${device.id}`);
      message.success('设备及关联点位已删除');
      load();
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const columns: ColumnsType<DeviceView> = [
    {
      title: '设备',
      dataIndex: 'name',
      key: 'name',
      width: 230,
      render: (_, device) => (
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span
              style={{
                width: 8,
                height: 8,
                borderRadius: '50%',
                background: device.connected ? '#22c55e' : device.connectionState === 'ERROR' ? '#ef4444' : '#cbd5e1',
                display: 'inline-block',
              }}
            />
            <a onClick={() => navigate(`/devices/${device.id}`)} style={{ fontWeight: 650 }}>
              {device.name}
            </a>
            {!device.enabled && <Tag>已禁用</Tag>}
          </div>
          <div className="tiny-muted" style={{ marginTop: 4 }}>
            {device.description || '未填写描述'}
          </div>
          <div className="tiny-muted" style={{ marginTop: 3 }}>
            认证：{authLabel(device.authMode)}
            {device.authMode === 'USERNAME' && device.username ? ` · ${device.username}` : ''}
          </div>
        </div>
      ),
    },
    {
      title: '端点',
      dataIndex: 'endpointUrl',
      key: 'endpointUrl',
      width: 250,
      render: (value, device) => (
        <div>
          <span className="node-id">{value}</span>
          {device.lastError && (
            <Tooltip title={device.lastError}>
              <div className="warning-inline" style={{ marginTop: 5 }}>
                最近连接失败，悬停查看原因
              </div>
            </Tooltip>
          )}
        </div>
      ),
    },
    {
      title: '安全',
      key: 'security',
      width: 200,
      render: (_, device) => (
        <Space size={6} wrap>
          <SecurityTag policy={device.securityPolicy} />
          <SecurityModeTag mode={device.securityMode} />
        </Space>
      ),
    },
    {
      title: '点位',
      dataIndex: 'tagCount',
      key: 'tagCount',
      width: 64,
      align: 'center',
    },
    {
      title: '状态',
      key: 'status',
      width: 92,
      render: (_, device) => (
        <ConnectionTag connected={device.connected} state={device.connectionState} error={device.lastError} />
      ),
    },
    {
      title: '操作',
      key: 'actions',
      fixed: 'right',
      width: 180,
      render: (_, device) => (
        <Space size={2}>
          {device.connected ? (
            <Tooltip title="断开连接">
              <Button
                size="small"
                type="text"
                icon={<DisconnectOutlined />}
                loading={busyId === device.id}
                onClick={() => disconnect(device)}
              />
            </Tooltip>
          ) : (
            <Tooltip title="连接">
              <Button
                size="small"
                type="text"
                icon={<ApiOutlined />}
                loading={busyId === device.id}
                onClick={() => connect(device)}
              />
            </Tooltip>
          )}
          <Tooltip title="设备详情">
            <Button
              size="small"
              type="text"
              icon={<LinkOutlined />}
              onClick={() => navigate(`/devices/${device.id}`)}
            />
          </Tooltip>
          <Tooltip title="编辑">
            <Button
              size="small"
              type="text"
              icon={<EditOutlined />}
              onClick={() => {
                setEditing(device);
                setDrawerOpen(true);
              }}
            />
          </Tooltip>
          <Popconfirm
            title="删除设备"
            description="关联点位和运行会话也会被删除，确定继续？"
            okText="删除"
            okButtonProps={{ danger: true }}
            cancelText="取消"
            onConfirm={() => remove(device)}
          >
            <Button size="small" type="text" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div className="page-stack">
      <PageHead
        title="OPC UA 设备"
        description="集中管理端点地址、安全策略、身份认证和连接参数。安全连接使用 OPC UA SecurityPolicy + MessageSecurityMode，不依赖 HTTPS。"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={load} loading={loading}>
              刷新
            </Button>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                setEditing(null);
                setDrawerOpen(true);
              }}
            >
              新增设备
            </Button>
          </Space>
        }
      />

      <Card className="table-card" variant="borderless">
        <div className="toolbar" style={{ marginBottom: 16 }}>
          <div className="toolbar-left">
            <Input
              allowClear
              prefix={<SearchOutlined />}
              placeholder="搜索设备名称、端点、用户名..."
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              style={{ width: 300 }}
            />
            <Select
              allowClear
              placeholder="安全策略"
              value={securityFilter}
              onChange={setSecurityFilter}
              style={{ width: 130 }}
              options={[
                { value: 'secure', label: '已加密' },
                { value: 'none', label: '未加密' },
              ]}
            />
            <Select
              allowClear
              placeholder="连接状态"
              value={statusFilter}
              onChange={setStatusFilter}
              style={{ width: 130 }}
              options={[
                { value: 'online', label: '在线' },
                { value: 'offline', label: '离线' },
                { value: 'error', label: '异常' },
              ]}
            />
            <span className="tiny-muted">共 {filtered.length} 台设备</span>
          </div>
        </div>

        {devices.length === 0 && !loading ? (
          <div className="empty-card">
            <CloudServerOutlined style={{ fontSize: 44, color: '#bfdbfe' }} />
            <div className="empty-title">还没有 OPC UA 设备</div>
            <div className="empty-desc">
              添加一台设备，填写端点地址、安全策略和认证方式。也可以从 Excel 模板批量导入设备与点位。
            </div>
            <Space style={{ marginTop: 18 }}>
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => {
                  setEditing(null);
                  setDrawerOpen(true);
                }}
              >
                新增第一台设备
              </Button>
              <Button onClick={() => navigate('/tags')}>前往点位配置</Button>
            </Space>
          </div>
        ) : (
          <Table<DeviceView>
            rowKey="id"
            columns={columns}
            dataSource={filtered}
            loading={loading}
            size="middle"
            scroll={{ x: 1020 }}
            pagination={{ pageSize: 10, showSizeChanger: true, showTotal: (total) => `共 ${total} 台` }}
            rowClassName="clickable-row"
            onRow={(device) => ({
              onDoubleClick: () => navigate(`/devices/${device.id}`),
            })}
          />
        )}
      </Card>

      <Alert
        type="info"
        showIcon
        message="安全连接提示"
        description="用户名密码认证在 SecurityPolicy=None 时不会加密，生产环境请使用 Basic256Sha256 / SignAndEncrypt。若服务器使用自签名证书，请在编辑设备时配置服务器证书固定，或临时启用“信任所有证书”完成调试。"
      />

      <DeviceFormDrawer
        open={drawerOpen}
        editing={editing}
        meta={meta}
        onClose={() => setDrawerOpen(false)}
        onSaved={load}
      />
    </div>
  );
}
