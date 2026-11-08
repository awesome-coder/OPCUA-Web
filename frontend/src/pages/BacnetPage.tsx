import React, { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Empty,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Tooltip,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ApiOutlined,
  CheckCircleOutlined,
  CloudDownloadOutlined,
  DeploymentUnitOutlined,
  DownloadOutlined,
  LinkOutlined,
  ReloadOutlined,
  SyncOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import type { BacnetMappingView, BacnetObjectView, DeviceView } from '../types';
import { api, errorText, download } from '../api';
import PageHead from '../components/PageHead';
import { AccessTag, DataTypeTag } from '../components/StatusTags';

export default function BacnetPage() {
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const [devices, setDevices] = useState<DeviceView[]>([]);
  const [mapping, setMapping] = useState<BacnetMappingView | null>(null);
  const [integration, setIntegration] = useState<any>(null);
  const [loading, setLoading] = useState(false);
  const [applying, setApplying] = useState(false);
  const selectedId = params.get('deviceId') || undefined;

  const loadDevices = async () => {
    setLoading(true);
    try {
      const [deviceResponse, integrationResponse] = await Promise.all([
        api.get<DeviceView[]>('/devices'),
        api.get('/bacnet/integration'),
      ]);
      setDevices(deviceResponse.data);
      setIntegration(integrationResponse.data);
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

  const loadMapping = async (deviceId: string) => {
    setLoading(true);
    try {
      const response = await api.get<BacnetMappingView>(`/bacnet/devices/${deviceId}/objects`);
      setMapping(response.data);
    } catch (error) {
      message.error(errorText(error));
      setMapping(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadDevices();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (selectedId) loadMapping(selectedId);
    else setMapping(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId]);

  const apply = async () => {
    if (!selectedId) return;
    try {
      setApplying(true);
      const response = await api.post<BacnetMappingView>(`/bacnet/devices/${selectedId}/apply`);
      setMapping(response.data);
      message.success('映射已写入点位配置');
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setApplying(false);
    }
  };

  const columns: ColumnsType<BacnetObjectView> = [
    {
      title: 'BACnet 对象',
      key: 'objectId',
      width: 190,
      fixed: 'left',
      render: (_, object) => (
        <div>
          <span className="mapping-object">{object.objectId}</span>
          <div className="tiny-muted" style={{ marginTop: 4 }}>
            {object.objectTypeLabel}
          </div>
        </div>
      ),
    },
    {
      title: '对象名称',
      dataIndex: 'objectName',
      key: 'objectName',
      width: 175,
      render: (value, object) => (
        <div>
          <div style={{ fontWeight: 600 }}>{value}</div>
          <div className="tiny-muted" style={{ marginTop: 2 }}>
            {object.units ? `${object.units} · ` : ''}
            {object.covIncrement !== undefined && object.covIncrement !== null
              ? `COV ${object.covIncrement}`
              : object.description || '—'}
          </div>
        </div>
      ),
    },
    {
      title: '属性',
      dataIndex: 'property',
      key: 'property',
      width: 110,
      render: (value) => <Tag className="mono">{value}</Tag>,
    },
    {
      title: 'OPC UA 点位',
      key: 'tag',
      width: 205,
      render: (_, object) => (
        <div>
          <div style={{ fontWeight: 600 }}>{object.tagName}</div>
          <div className="node-id" style={{ marginTop: 3 }}>
            {object.nodeId}
          </div>
        </div>
      ),
    },
    {
      title: '类型',
      key: 'type',
      width: 95,
      render: (_, object) => <DataTypeTag type={object.dataType} array={object.array} />,
    },
    {
      title: '访问',
      dataIndex: 'accessMode',
      key: 'accessMode',
      width: 74,
      render: (value) => <AccessTag mode={value} />,
    },
    {
      title: '来源',
      dataIndex: 'mappingSource',
      key: 'mappingSource',
      width: 90,
      render: (value) => (
        <Tag color={value === 'OVERRIDE' ? 'purple' : 'default'}>
          {value === 'OVERRIDE' ? '手动' : '自动'}
        </Tag>
      ),
    },
    {
      title: '提示',
      key: 'warnings',
      width: 170,
      render: (_, object) =>
        object.warnings.length ? (
          <Tooltip title={object.warnings.join('；')}>
            <span className="warning-inline">{object.warnings.length} 条映射提示</span>
          </Tooltip>
        ) : (
          <span className="tiny-muted">—</span>
        ),
    },
  ];

  return (
    <div className="page-stack">
      <PageHead
        title="BACnet 对象映射"
        description="将 OPC UA 点位按数据类型映射为 BACnet 对象，为后续 BACnet/IP 网关或协议栈提供稳定接口。当前实现映射、COV 配置与写入端口，未内置 BACnet 协议栈。"
        extra={
          <Space>
            <Select
              style={{ width: 260 }}
              placeholder="选择设备"
              value={selectedId}
              showSearch
              optionFilterProp="label"
              onChange={(value) => setParams({ deviceId: value })}
              options={devices.map((device) => ({ value: device.id, label: device.name }))}
            />
            <Button
              icon={<ReloadOutlined />}
              onClick={() => selectedId && loadMapping(selectedId)}
              loading={loading}
            >
              刷新映射
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
        <>
          <Card variant="borderless" className="panel-pad">
            <div className="bacnet-header">
              <div className="bacnet-logo">
                <ApiOutlined />
              </div>
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: 16, fontWeight: 700 }}>
                  {mapping?.deviceName || 'BACnet 映射'}
                </div>
                <div className="tiny-muted" style={{ marginTop: 3 }}>
                  根据数据类型、读写属性和枚举定义生成 BACnet 对象建议
                </div>
              </div>
              <Space wrap>
                <Tag icon={<CheckCircleOutlined />} color="green">
                  {mapping?.objectCount || 0} 个对象
                </Tag>
                <Button
                  type="primary"
                  icon={<SyncOutlined />}
                  loading={applying}
                  onClick={apply}
                  disabled={!mapping || mapping.objectCount === 0}
                >
                  应用映射到点位
                </Button>
                <Button
                  icon={<DownloadOutlined />}
                  onClick={() =>
                    download(
                      `/bacnet/devices/${selectedId}/objects.csv`,
                      `bacnet-objects-${selectedId}.csv`,
                    ).catch((error) => message.error(errorText(error)))
                  }
                  disabled={!mapping || mapping.objectCount === 0}
                >
                  导出 CSV
                </Button>
              </Space>
            </div>
          </Card>

          {mapping?.warnings && mapping.warnings.length > 0 && (
            <Alert
              type="warning"
              showIcon
              message="映射提示"
              description={mapping.warnings.slice(0, 8).join('；')}
            />
          )}

          <Card variant="borderless" className="table-card">
            <Table<BacnetObjectView>
              rowKey="tagId"
              columns={columns}
              dataSource={mapping?.objects || []}
              loading={loading}
              pagination={{ pageSize: 12, showSizeChanger: true }}
              scroll={{ x: 1115 }}
              locale={{
                emptyText: '当前设备还没有可映射的已启用点位，请先在点位配置中创建点位。',
              }}
            />
          </Card>

          <Row gutter={16}>
            <Col xs={24} lg={14}>
              <Card variant="borderless" title="扩展接口设计">
                <div className="extension-list">
                  {(integration?.extensionPoints || []).map((item: string) => (
                    <div className="extension-item" key={item}>
                      <DeploymentUnitOutlined style={{ color: '#0f766e', marginTop: 2 }} />
                      <span>{item}</span>
                    </div>
                  ))}
                  <div className="extension-item">
                    <ThunderboltOutlined style={{ color: '#0f766e', marginTop: 2 }} />
                    <span>
                      订阅值在标准化后通过 <span className="mono">BacnetPointSink</span>{' '}
                      推送；外部 BACnet 写入可调用 <span className="mono">BacnetWritePort</span>。
                    </span>
                  </div>
                </div>
              </Card>
            </Col>
            <Col xs={24} lg={10}>
              <Card variant="borderless" title="默认映射规则">
                <div className="flow">
                  {[
                    ['只读数值', 'Analog Input (AI)'],
                    ['读写数值', 'Analog Value (AV)'],
                    ['布尔量', 'Binary Input / Binary Value'],
                    ['带枚举的整型', 'Multi-state Input / Value'],
                    ['字符串 / 数组', 'CharacterString Value (type 40)'],
                  ].map(([left, right], index) => (
                    <div className="flow-step" key={left}>
                      <div className="flow-num">{index + 1}</div>
                      <div>
                        <div className="flow-title">{left}</div>
                        <div className="flow-desc">{right}</div>
                      </div>
                    </div>
                  ))}
                </div>
                <div className="tiny-muted" style={{ marginTop: 14, lineHeight: 1.7 }}>
                  可在点位表单中手动覆盖对象类型、实例号和属性；应用映射后会写入点位配置。
                </div>
              </Card>
            </Col>
          </Row>

          <Alert
            type="info"
            showIcon
            message="关于 BACnet 协议栈"
            description="本工具聚焦 OPC UA 配置、数据访问和映射模型。对象 ID、实例号、属性和 COV 增量已就绪；实际 BACnet/IP 设备服务、对象生命周期和 COV 通知需要由后续适配器实现。"
          />
        </>
      )}
    </div>
  );
}
