import React, { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Empty,
  Modal,
  Row,
  Space,
  Spin,
  Table,
  Tabs,
  Tag,
  Tooltip,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ApiOutlined,
  ArrowLeftOutlined,
  CloudServerOutlined,
  DeploymentUnitOutlined,
  DisconnectOutlined,
  EditOutlined,
  LinkOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { useNavigate, useParams } from 'react-router-dom';
import type { DeviceView, EndpointView, MetaView } from '../types';
import { api, displayDate, errorText } from '../api';
import DeviceFormDrawer from '../components/DeviceFormDrawer';
import TagTable from '../components/TagTable';
import {
  ConnectionTag,
  SecurityModeTag,
  SecurityTag,
  authLabel,
} from '../components/StatusTags';

export default function DeviceDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [device, setDevice] = useState<DeviceView | null>(null);
  const [meta, setMeta] = useState<MetaView | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [endpoints, setEndpoints] = useState<EndpointView[] | null>(null);
  const [endpointOpen, setEndpointOpen] = useState(false);
  const [endpointLoading, setEndpointLoading] = useState(false);
  const [activeTab, setActiveTab] = useState('overview');

  const load = async () => {
    if (!id) return;
    try {
      const [deviceResponse, metaResponse] = await Promise.all([
        api.get<DeviceView>(`/devices/${id}`),
        meta ? Promise.resolve({ data: meta }) : api.get<MetaView>('/meta'),
      ]);
      setDevice(deviceResponse.data);
      setMeta(metaResponse.data);
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    setLoading(true);
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  useEffect(() => {
    if (!id) return;
    const timer = window.setInterval(() => {
      api.get<DeviceView>(`/devices/${id}`)
        .then((response) => setDevice(response.data))
        .catch(() => undefined);
    }, 15_000);
    return () => window.clearInterval(timer);
  }, [id]);

  const connect = async () => {
    if (!device) return;
    setBusy(true);
    try {
      await api.post(`/devices/${device.id}/connect`);
      message.success('连接成功');
      load();
    } catch (error) {
      message.error(errorText(error));
      load();
    } finally {
      setBusy(false);
    }
  };

  const disconnect = async () => {
    if (!device) return;
    setBusy(true);
    try {
      await api.post(`/devices/${device.id}/disconnect`);
      message.success('已断开');
      load();
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setBusy(false);
    }
  };

  const discover = async () => {
    if (!device) return;
    setEndpointLoading(true);
    setEndpointOpen(true);
    try {
      const response = await api.get<EndpointView[]>(`/devices/${device.id}/endpoints`, {
        timeout: 60_000,
      });
      setEndpoints(response.data);
    } catch (error) {
      message.error(errorText(error));
      setEndpoints([]);
    } finally {
      setEndpointLoading(false);
    }
  };

  const endpointColumns: ColumnsType<EndpointView> = [
    {
      title: '端点 URL',
      dataIndex: 'endpointUrl',
      key: 'endpointUrl',
      width: 300,
      render: (value) => <span className="node-id">{value}</span>,
    },
    {
      title: '安全策略',
      dataIndex: 'securityPolicy',
      key: 'securityPolicy',
      width: 170,
      render: (value) => <Tag>{value}</Tag>,
    },
    {
      title: '模式',
      dataIndex: 'securityMode',
      key: 'securityMode',
      width: 110,
      render: (value) => <Tag color={value === 'None' ? 'default' : 'blue'}>{value}</Tag>,
    },
    {
      title: '用户令牌',
      dataIndex: 'userTokenTypes',
      key: 'userTokenTypes',
      width: 180,
      render: (values: string[]) => (
        <Space size={4} wrap>
          {values.map((value) => (
            <Tag key={value} style={{ fontSize: 10 }}>
              {value}
            </Tag>
          ))}
        </Space>
      ),
    },
    {
      title: '安全等级',
      dataIndex: 'securityLevel',
      key: 'securityLevel',
      width: 90,
      align: 'center',
    },
    {
      title: '服务器证书 SHA-256',
      dataIndex: 'certificateFingerprintSha256',
      key: 'certificateFingerprintSha256',
      render: (value) => (
        <Tooltip title={value}>
          <span className="mono" style={{ fontSize: 10 }}>
            {value ? `${value.slice(0, 23)}…` : '—'}
          </span>
        </Tooltip>
      ),
    },
  ];

  if (loading) {
    return (
      <div style={{ padding: 80, textAlign: 'center' }}>
        <Spin size="large" />
      </div>
    );
  }

  if (!device) {
    return (
      <Card variant="borderless">
        <Empty description="设备不存在或已删除">
          <Button type="primary" onClick={() => navigate('/devices')}>
            返回设备列表
          </Button>
        </Empty>
      </Card>
    );
  }

  return (
    <div className="page-stack">
      <div className="detail-head">
        <div>
          <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate('/devices')}>
            返回设备列表
          </Button>
          <div className="detail-title" style={{ marginTop: 4 }}>
            <h2>{device.name}</h2>
            <ConnectionTag connected={device.connected} state={device.connectionState} error={device.lastError} />
            <SecurityTag policy={device.securityPolicy} />
            <SecurityModeTag mode={device.securityMode} />
            {!device.enabled && <Tag>已禁用</Tag>}
          </div>
          <div className="detail-meta">
            <span>
              <CloudServerOutlined /> <span className="mono">{device.endpointUrl}</span>
            </span>
            <span>认证：{authLabel(device.authMode)}</span>
            <span>点位：{device.tagCount}</span>
            <span>更新：{displayDate(device.updatedAt)}</span>
          </div>
        </div>
        <Space wrap>
          {device.connected ? (
            <Button danger icon={<DisconnectOutlined />} onClick={disconnect} loading={busy}>
              断开
            </Button>
          ) : (
            <Button type="primary" icon={<ApiOutlined />} onClick={connect} loading={busy} disabled={!device.enabled}>
              连接
            </Button>
          )}
          <Button icon={<ReloadOutlined />} onClick={load}>
            刷新
          </Button>
          <Button icon={<EditOutlined />} onClick={() => setDrawerOpen(true)}>
            编辑设备
          </Button>
        </Space>
      </div>

      {device.lastError && (
        <Alert
          type="error"
          showIcon
          message="最近一次连接错误"
          description={device.lastError}
        />
      )}

      <Card variant="borderless">
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={[
            {
              key: 'overview',
              label: '设备概览',
              children: (
                <div className="page-stack">
                  <div className="info-grid">
                    <div className="info-item">
                      <div className="info-item-label">连接状态</div>
                      <div className="info-item-value">
                        {device.connected ? '在线 / 会话已建立' : device.connectionState === 'ERROR' ? '连接异常' : '离线'}
                      </div>
                    </div>
                    <div className="info-item">
                      <div className="info-item-label">连接时间</div>
                      <div className="info-item-value">{displayDate(device.connectedAt)}</div>
                    </div>
                    <div className="info-item">
                      <div className="info-item-label">安全策略</div>
                      <div className="info-item-value">{device.securityPolicy}</div>
                    </div>
                    <div className="info-item">
                      <div className="info-item-label">安全模式</div>
                      <div className="info-item-value">{device.securityMode}</div>
                    </div>
                    <div className="info-item">
                      <div className="info-item-label">认证方式</div>
                      <div className="info-item-value">{authLabel(device.authMode)}</div>
                    </div>
                    <div className="info-item">
                      <div className="info-item-label">证书信任</div>
                      <div className="info-item-value">
                        {device.trustAllCertificates
                          ? '信任所有（调试）'
                          : device.hasServerCertificate
                            ? '固定服务器证书'
                            : '未配置固定证书'}
                      </div>
                    </div>
                  </div>

                  <Row gutter={16}>
                    <Col xs={24} lg={14}>
                      <Card
                        variant="borderless"
                        type="inner"
                        title="连接参数"
                        extra={
                          <Button size="small" onClick={discover} icon={<SafetyCertificateOutlined />}>
                            探测端点
                          </Button>
                        }
                      >
                        <Descriptions column={2} size="small" bordered>
                          <Descriptions.Item label="端点地址" span={2}>
                            <span className="mono">{device.endpointUrl}</span>
                          </Descriptions.Item>
                          <Descriptions.Item label="连接超时">{device.connectTimeoutMs} ms</Descriptions.Item>
                          <Descriptions.Item label="请求超时">{device.requestTimeoutMs} ms</Descriptions.Item>
                          <Descriptions.Item label="会话超时">{device.sessionTimeoutMs} ms</Descriptions.Item>
                          <Descriptions.Item label="订阅发布周期">{device.publishingIntervalMs} ms</Descriptions.Item>
                          <Descriptions.Item label="服务器证书">
                            {device.hasServerCertificate ? '已保存固定证书' : '未保存'}
                          </Descriptions.Item>
                          <Descriptions.Item label="密码">
                            {device.hasPassword ? '已加密保存' : '未配置'}
                          </Descriptions.Item>
                          <Descriptions.Item label="描述" span={2}>
                            {device.description || '—'}
                          </Descriptions.Item>
                        </Descriptions>
                      </Card>
                    </Col>
                    <Col xs={24} lg={10}>
                      <Card variant="borderless" type="inner" title="操作">
                        <Space direction="vertical" style={{ width: '100%' }} size={10}>
                          <Button
                            block
                            icon={<DeploymentUnitOutlined />}
                            onClick={() => navigate(`/browse?deviceId=${device.id}`)}
                          >
                            浏览地址空间 / 扫描点位
                          </Button>
                          <Button
                            block
                            icon={<ThunderboltOutlined />}
                            onClick={() => navigate(`/live?deviceId=${device.id}`)}
                          >
                            启动实时数据订阅
                          </Button>
                          <Button
                            block
                            icon={<ApiOutlined />}
                            onClick={() => navigate(`/bacnet?deviceId=${device.id}`)}
                          >
                            查看 BACnet 映射
                          </Button>
                          <Button
                            block
                            icon={<LinkOutlined />}
                            onClick={() => setActiveTab('tags')}
                          >
                            管理点位配置
                          </Button>
                        </Space>
                      </Card>
                    </Col>
                  </Row>
                </div>
              ),
            },
            {
              key: 'tags',
              label: `点位配置 (${device.tagCount})`,
              children: <TagTable deviceId={device.id} allowImport={false} />,
            },
          ]}
        />
      </Card>

      <DeviceFormDrawer
        open={drawerOpen}
        editing={device}
        meta={meta}
        onClose={() => setDrawerOpen(false)}
        onSaved={load}
      />

      <Modal
        title="服务器端点"
        open={endpointOpen}
        onCancel={() => setEndpointOpen(false)}
        footer={<Button onClick={() => setEndpointOpen(false)}>关闭</Button>}
        width={1100}
      >
        <Table<EndpointView>
          rowKey={(row) => `${row.endpointUrl}-${row.securityPolicy}-${row.securityMode}`}
          dataSource={endpoints || []}
          columns={endpointColumns}
          loading={endpointLoading}
          size="small"
          pagination={false}
          scroll={{ x: 1000 }}
          locale={{ emptyText: '没有发现端点' }}
        />
      </Modal>
    </div>
  );
}
