import React, { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Progress,
  Row,
  Space,
  Tag,
  Tooltip,
  message,
} from 'antd';
import {
  ApiOutlined,
  ArrowRightOutlined,
  CloudServerOutlined,
  CodeOutlined,
  DatabaseOutlined,
  DeploymentUnitOutlined,
  DownloadOutlined,
  FileExcelOutlined,
  KeyOutlined,
  LockOutlined,
  PlusOutlined,
  SafetyCertificateOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import type { DashboardView, DeviceView, MetaView } from '../types';
import { api, errorText, formatValue } from '../api';
import PageHead from '../components/PageHead';
import DeviceFormDrawer from '../components/DeviceFormDrawer';
import { ConnectionTag, SecurityTag, authLabel } from '../components/StatusTags';

const statDefs = [
  { key: 'deviceCount', label: 'OPC UA 设备', accent: '#2563eb', icon: <CloudServerOutlined /> },
  { key: 'connectedDeviceCount', label: '在线会话', accent: '#16a34a', icon: <ApiOutlined /> },
  { key: 'tagCount', label: '配置点位', accent: '#7c3aed', icon: <DatabaseOutlined /> },
  { key: 'secureDeviceCount', label: '安全设备', accent: '#0891b2', icon: <LockOutlined /> },
] as const;

export default function DashboardPage() {
  const navigate = useNavigate();
  const [dashboard, setDashboard] = useState<DashboardView | null>(null);
  const [meta, setMeta] = useState<MetaView | null>(null);
  const [loading, setLoading] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const [dashboardResponse, metaResponse] = await Promise.all([
        api.get<DashboardView>('/dashboard'),
        meta ? Promise.resolve({ data: meta }) : api.get<MetaView>('/meta'),
      ]);
      setDashboard(dashboardResponse.data);
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

  const devices = dashboard?.devices || [];
  const online = dashboard?.connectedDeviceCount || 0;
  const total = dashboard?.deviceCount || 0;
  const onlinePercent = total === 0 ? 0 : Math.round((online / total) * 100);

  return (
    <div className="page-stack">
      <section className="hero">
        <div className="hero-content">
          <div>
            <span className="hero-eyebrow">
              <CodeOutlined /> INDUSTRIAL INTEGRATION CONSOLE
            </span>
            <h1>
              OPC UA 配置、监视与 BACnet
              <br />
              映射控制台
            </h1>
            <p>
              基于 Eclipse Milo 与 Spring Boot 构建。统一管理设备安全连接、点位数据转换、地址空间扫描、
              实时订阅和 Excel 批量配置，并为 BACnet 对象映射预留扩展接口。
            </p>
            <div className="hero-actions">
              <Button type="primary" size="large" icon={<PlusOutlined />} onClick={() => setDrawerOpen(true)}>
                新增 OPC UA 设备
              </Button>
              <Button
                size="large"
                ghost
                icon={<DeploymentUnitOutlined />}
                onClick={() => navigate('/browse')}
              >
                浏览地址空间
              </Button>
            </div>
            <div className="hero-meta">
              <span className="hero-meta-item">
                <span className={`status-dot ${online > 0 ? 'online' : 'offline'}`} />
                <b>{online}</b> 个在线会话
              </span>
              <span className="hero-meta-item">
                <DatabaseOutlined /> <b>{dashboard?.tagCount || 0}</b> 个点位
              </span>
              <span className="hero-meta-item">
                <SafetyCertificateOutlined /> <b>{dashboard?.secureDeviceCount || 0}</b> 个安全端点
              </span>
              <span className="hero-meta-item">
                <ThunderboltOutlined /> Milo 0.6.16
              </span>
            </div>
          </div>
          <div className="hero-illustration">
            <div className="ring" />
            <div className="ring two" />
            <ApiOutlined className="core" />
          </div>
        </div>
      </section>

      <div className="stat-grid">
        {statDefs.map((definition) => (
          <div className="stat-card" key={definition.key} style={{ ['--accent' as any]: definition.accent }}>
            <div className="stat-top">
              <span className="stat-label">{definition.label}</span>
              <span className="stat-icon">{definition.icon}</span>
            </div>
            <div className="stat-value">{dashboard?.[definition.key] ?? 0}</div>
            <div className="stat-foot">
              {definition.key === 'deviceCount'
                ? `${dashboard?.enabledDeviceCount || 0} 台已启用`
                : definition.key === 'connectedDeviceCount'
                  ? `在线率 ${onlinePercent}%`
                  : definition.key === 'tagCount'
                    ? `${dashboard?.bacnetMappedCount || 0} 个已映射 BACnet`
                    : '使用加密策略的设备'}
            </div>
          </div>
        ))}
      </div>

      <div className="dashboard-grid">
        <Card
          variant="borderless"
          loading={loading}
          title={
            <Space>
              <CloudServerOutlined />
              <span>设备运行状态</span>
            </Space>
          }
          extra={
            <Button type="link" onClick={() => navigate('/devices')}>
              全部设备 <ArrowRightOutlined />
            </Button>
          }
        >
          {devices.length === 0 ? (
            <div className="empty-card">
              <CloudServerOutlined style={{ fontSize: 42, color: '#bfdbfe' }} />
              <div className="empty-title">从一台 OPC UA 设备开始</div>
              <div className="empty-desc">
                填写端点地址并选择安全策略。控制台会自动探测服务器端点、匹配认证方式，并支持浏览和订阅。
              </div>
              <Space style={{ marginTop: 16 }}>
                <Button type="primary" onClick={() => setDrawerOpen(true)}>
                  新增设备
                </Button>
                <Button
                  icon={<FileExcelOutlined />}
                  onClick={() => {
                    window.open('/api/excel/template', '_blank');
                  }}
                >
                  下载 Excel 模板
                </Button>
              </Space>
            </div>
          ) : (
            <div className="device-mini-list">
              {devices.slice(0, 6).map((device) => (
                <div className="device-mini" key={device.id}>
                  <div style={{ minWidth: 0 }}>
                    <div className="device-mini-name">
                      <span
                        style={{
                          width: 8,
                          height: 8,
                          borderRadius: '50%',
                          display: 'inline-block',
                          background: device.connected ? '#22c55e' : '#cbd5e1',
                        }}
                      />
                      {device.name}
                      {!device.enabled && <Tag>禁用</Tag>}
                    </div>
                    <Tooltip title={device.endpointUrl}>
                      <div className="device-mini-endpoint">{device.endpointUrl}</div>
                    </Tooltip>
                  </div>
                  <Space size={6} wrap>
                    <SecurityTag policy={device.securityPolicy} />
                    <Tag>{authLabel(device.authMode)}</Tag>
                    <ConnectionTag
                      connected={device.connected}
                      state={device.connectionState}
                      error={device.lastError}
                    />
                    <Button
                      size="small"
                      type="text"
                      onClick={() => navigate(`/devices/${device.id}`)}
                    >
                      详情
                    </Button>
                  </Space>
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card variant="borderless" title="快捷操作">
          <div className="quick-grid">
            {[
              {
                icon: <DeploymentUnitOutlined />,
                title: '扫描地址空间',
                desc: '浏览节点树并批量创建点位',
                action: () => navigate('/browse'),
              },
              {
                icon: <ThunderboltOutlined />,
                title: '启动实时订阅',
                desc: '基于 Subscription 的实时值',
                action: () => navigate('/live'),
              },
              {
                icon: <ApiOutlined />,
                title: '查看 BACnet 映射',
                desc: '按数据类型生成对象模型',
                action: () => navigate('/bacnet'),
              },
              {
                icon: <DatabaseOutlined />,
                title: '配置数据点位',
                desc: '数据类型、换算、死区与枚举',
                action: () => navigate('/tags'),
              },
              {
                icon: <FileExcelOutlined />,
                title: 'Excel 批量导入',
                desc: '设备与点位统一配置',
                action: () => navigate('/tags'),
              },
              {
                icon: <KeyOutlined />,
                title: '客户端证书',
                desc: '下载证书并查看安全配置',
                action: () => navigate('/settings'),
              },
            ].map((item) => (
              <button type="button" className="quick-action" key={item.title} onClick={item.action}>
                <span className="quick-icon">{item.icon}</span>
                <span>
                  <span className="quick-title">{item.title}</span>
                  <span className="quick-desc">{item.desc}</span>
                </span>
              </button>
            ))}
          </div>
        </Card>
      </div>

      <Row gutter={16}>
        <Col xs={24} lg={15}>
          <Card variant="borderless" title="数据流与集成架构">
            <div className="flow">
              {[
                {
                  title: '端点与安全',
                  desc: 'DiscoveryClient 获取端点，按 SecurityPolicy / Mode 与认证方式匹配，建立 SecureChannel 和会话。',
                },
                {
                  title: '地址空间与点位',
                  desc: 'Browse / Read 属性获取数据类型、访问权限和工程单位；点位定义负责类型转换、缩放与偏移。',
                },
                {
                  title: '实时订阅',
                  desc: 'UaSubscription + MonitoredItem 接收数据变更，支持采样周期、队列、死区和质量码，通过 SSE 推送到浏览器。',
                },
                {
                  title: 'BACnet 映射接口',
                  desc: '按数据类型映射为 AI/AV/BI/BV/MSI/MSV/CSV；BacnetPointSink 与 BacnetWritePort 为协议栈适配器预留。',
                },
              ].map((item, index) => (
                <div className="flow-step" key={item.title}>
                  <div className="flow-num">{index + 1}</div>
                  <div>
                    <div className="flow-title">{item.title}</div>
                    <div className="flow-desc">{item.desc}</div>
                  </div>
                </div>
              ))}
            </div>
          </Card>
        </Col>
        <Col xs={24} lg={9}>
          <Card variant="borderless" title="工程能力">
            <Space direction="vertical" size={14} style={{ width: '100%' }}>
              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, marginBottom: 5 }}>
                  <span>设备在线率</span>
                  <span style={{ fontWeight: 650 }}>{onlinePercent}%</span>
                </div>
                <Progress percent={onlinePercent} showInfo={false} strokeColor="#16a34a" />
              </div>
              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, marginBottom: 5 }}>
                  <span>BACnet 映射覆盖</span>
                  <span style={{ fontWeight: 650 }}>
                    {dashboard?.tagCount
                      ? Math.round(((dashboard.bacnetMappedCount || 0) / dashboard.tagCount) * 100)
                      : 0}
                    %
                  </span>
                </div>
                <Progress
                  percent={
                    dashboard?.tagCount
                      ? Math.round(((dashboard.bacnetMappedCount || 0) / dashboard.tagCount) * 100)
                      : 0
                  }
                  showInfo={false}
                  strokeColor="#0d9488"
                />
              </div>
              <div className="info-item">
                <div className="info-item-label">客户端证书 SHA-256</div>
                <div className="mono" style={{ fontSize: 10, lineHeight: 1.6, wordBreak: 'break-all' }}>
                  {dashboard?.clientCertificateFingerprint || '—'}
                </div>
              </div>
              <Space wrap>
                <Tag color="blue">Milo</Tag>
                <Tag color="cyan">Spring Boot</Tag>
                <Tag color="purple">React</Tag>
                <Tag color="geekblue">Ant Design</Tag>
                <Tag color="green">SSE</Tag>
                <Tag color="gold">Excel</Tag>
              </Space>
            </Space>
          </Card>
        </Col>
      </Row>

      {devices.some((device) => device.connectionState === 'ERROR') && (
        <Alert
          type="error"
          showIcon
          message="有设备连接异常"
          description="查看设备详情中的错误信息，检查端点地址、安全策略、认证方式或服务器证书信任配置。"
          action={
            <Button size="small" onClick={() => navigate('/devices')}>
              查看设备
            </Button>
          }
        />
      )}

      <DeviceFormDrawer
        open={drawerOpen}
        meta={meta}
        onClose={() => setDrawerOpen(false)}
        onSaved={load}
      />
    </div>
  );
}
