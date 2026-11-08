import React, { useEffect, useState } from 'react';
import { Alert, Button, Card, Select, Space, Tag, message } from 'antd';
import { CloudServerOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import type { DeviceView } from '../types';
import { api, errorText } from '../api';
import PageHead from '../components/PageHead';
import TagTable from '../components/TagTable';
import { ConnectionTag, SecurityTag } from '../components/StatusTags';

export default function TagsPage() {
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const [devices, setDevices] = useState<DeviceView[]>([]);
  const [loading, setLoading] = useState(false);
  const selectedId = params.get('deviceId') || undefined;

  const load = async () => {
    setLoading(true);
    try {
      const response = await api.get<DeviceView[]>('/devices');
      setDevices(response.data);
      if (!response.data.some((device) => device.id === selectedId)) {
        if (response.data.length > 0) {
          setParams({ deviceId: response.data[0].id }, { replace: true });
        } else {
          setParams({}, { replace: true });
        }
      }
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

  const selected = devices.find((device) => device.id === selectedId);

  return (
    <div className="page-stack">
      <PageHead
        title="点位配置"
        description="定义 OPC UA 变量到业务点位的映射，包括数据类型、访问方式、工程换算、订阅参数和 BACnet 对象类型。"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={load} loading={loading}>
              刷新设备
            </Button>
            <Button icon={<PlusOutlined />} onClick={() => navigate('/devices')}>
              新增设备
            </Button>
          </Space>
        }
      />

      {devices.length === 0 && !loading ? (
        <Card variant="borderless">
          <div className="empty-card">
            <CloudServerOutlined style={{ fontSize: 44, color: '#bfdbfe' }} />
            <div className="empty-title">请先添加 OPC UA 设备</div>
            <div className="empty-desc">点位必须归属于一台设备。创建并连接设备后，即可配置和批量导入点位。</div>
            <Button type="primary" style={{ marginTop: 18 }} onClick={() => navigate('/devices')}>
              前往设备管理
            </Button>
          </div>
        </Card>
      ) : (
        <>
          <Card variant="borderless" className="panel-pad">
            <div className="toolbar">
              <div className="toolbar-left">
                <span style={{ fontSize: 12, color: '#64748b' }}>当前设备</span>
                <Select
                  style={{ width: 320 }}
                  value={selectedId}
                  placeholder="选择设备"
                  showSearch
                  optionFilterProp="label"
                  onChange={(value) => setParams({ deviceId: value })}
                  options={devices.map((device) => ({
                    value: device.id,
                    label: device.name,
                  }))}
                />
                {selected && (
                  <>
                    <ConnectionTag
                      connected={selected.connected}
                      state={selected.connectionState}
                      error={selected.lastError}
                    />
                    <SecurityTag policy={selected.securityPolicy} />
                    <span className="tiny-muted">{selected.tagCount} 个点位</span>
                    <Tag className="mono" style={{ fontSize: 10 }}>
                      {selected.endpointUrl}
                    </Tag>
                  </>
                )}
              </div>
              <div className="toolbar-right">
                {selected && (
                  <Button onClick={() => navigate(`/devices/${selected.id}`)}>设备详情</Button>
                )}
                <Button onClick={() => navigate(`/browse?deviceId=${selectedId || ''}`)}>
                  浏览地址空间
                </Button>
              </div>
            </div>
          </Card>

          {selected && !selected.connected && (
            <Alert
              type="warning"
              showIcon
              message="设备尚未连接"
              description="读取、写入、地址空间浏览和实时订阅时会自动尝试建立连接；批量导入和点位编辑不需要在线。"
            />
          )}

          <TagTable
            key={selectedId || 'none'}
            deviceId={selectedId}
            allowImport
            showDeviceColumn={false}
          />
        </>
      )}
    </div>
  );
}
