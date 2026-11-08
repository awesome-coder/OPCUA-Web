import React, { useEffect, useMemo, useRef, useState } from 'react';
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
  DisconnectOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  ThunderboltOutlined,
  WifiOutlined,
} from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import type { DeviceView, MetaView, TagValueView, TagView } from '../types';
import { api, errorText, formatValue } from '../api';
import PageHead from '../components/PageHead';
import { DataTypeTag, QualityTag } from '../components/StatusTags';

type LiveState = 'idle' | 'connecting' | 'live' | 'reconnecting' | 'error';

function formatLiveValue(tag: TagView, value: unknown): string {
  if (tag.dataType === 'DATETIME' && typeof value === 'string' && !Number.isNaN(Date.parse(value))) {
    return new Date(value).toLocaleString('zh-CN', { hour12: false });
  }
  return formatValue(value);
}

function formatTime(value?: string): string {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('zh-CN', { hour12: false });
}

export default function LivePage() {
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const [devices, setDevices] = useState<DeviceView[]>([]);
  const [meta, setMeta] = useState<MetaView | null>(null);
  const [tags, setTags] = useState<TagView[]>([]);
  const [selectedTagIds, setSelectedTagIds] = useState<string[]>([]);
  const [state, setState] = useState<LiveState>('idle');
  const [values, setValues] = useState<Record<string, TagValueView>>({});
  const [eventCount, setEventCount] = useState(0);
  const [lastEventAt, setLastEventAt] = useState<string | null>(null);
  const [flashId, setFlashId] = useState<string | null>(null);
  const [readyInfo, setReadyInfo] = useState<any>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const sourceRef = useRef<EventSource | null>(null);
  const flashTimer = useRef<number | null>(null);
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

  const loadTags = async (deviceId: string) => {
    try {
      const response = await api.get<TagView[]>('/tags', { params: { deviceId } });
      const enabled = response.data.filter((tag) => tag.enabled);
      setTags(enabled);
      setSelectedTagIds((current) => {
        const valid = current.filter((id) => enabled.some((tag) => tag.id === id));
        return valid.length ? valid : enabled.slice(0, Math.min(8, enabled.length)).map((tag) => tag.id);
      });
    } catch (error) {
      message.error(errorText(error));
    }
  };

  useEffect(() => {
    loadDevices();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    stop(false);
    setValues({});
    setEventCount(0);
    setErrorMessage(null);
    setReadyInfo(null);
    if (selectedId) loadTags(selectedId);
    else setTags([]);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId]);

  useEffect(() => {
    return () => {
      stop(false);
      if (flashTimer.current) window.clearTimeout(flashTimer.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const stop = (notify = true) => {
    if (sourceRef.current) {
      sourceRef.current.close();
      sourceRef.current = null;
    }
    setState('idle');
    if (notify) message.info('实时订阅已停止');
  };

  const start = async () => {
    if (!selectedId) {
      message.warning('请选择设备');
      return;
    }
    if (selectedTagIds.length === 0) {
      message.warning('请至少选择一个点位');
      return;
    }
    stop(false);
    setValues({});
    setEventCount(0);
    setErrorMessage(null);
    setReadyInfo(null);
    setState('connecting');
    try {
      await api.post(`/devices/${selectedId}/connect`);
    } catch (error) {
      setErrorMessage(errorText(error));
      setState('error');
      return;
    }
    const query = new URLSearchParams();
    if (selectedTagIds.length) query.set('tagIds', selectedTagIds.join(','));
    const source = new EventSource(`/api/devices/${selectedId}/live?${query.toString()}`);
    sourceRef.current = source;

    source.addEventListener('ready', (event) => {
      const data = JSON.parse((event as MessageEvent).data || '{}');
      setReadyInfo(data);
      setState('live');
      setErrorMessage(null);
    });
    source.addEventListener('subscribed', () => setState('live'));
    source.addEventListener('value', (event) => {
      const value = JSON.parse((event as MessageEvent).data) as TagValueView;
      setValues((current) => ({ ...current, [value.tagId]: value }));
      setEventCount((count) => count + 1);
      setLastEventAt(new Date().toLocaleTimeString('zh-CN', { hour12: false }));
      setState('live');
      setFlashId(value.tagId);
      if (flashTimer.current) window.clearTimeout(flashTimer.current);
      flashTimer.current = window.setTimeout(() => setFlashId(null), 700);
    });
    source.addEventListener('error', (event) => {
      const messageEvent = event as MessageEvent;
      if (messageEvent.data) {
        try {
          const data = JSON.parse(messageEvent.data);
          setErrorMessage(data.message || '订阅发生错误');
          setState('error');
        } catch {
          setErrorMessage(messageEvent.data);
          setState('error');
        }
      }
    });
    source.addEventListener('closed', (event) => {
      try {
        const data = JSON.parse((event as MessageEvent).data || '{}');
        setErrorMessage(data.message || null);
      } catch {
        // ignore
      }
      setState('idle');
      source.close();
      if (sourceRef.current === source) sourceRef.current = null;
    });
    source.onerror = () => {
      if (source.readyState === EventSource.CLOSED) {
        setState('idle');
      } else {
        setState((current) => (current === 'error' ? current : 'reconnecting'));
      }
    };
  };

  const selectedDevice = devices.find((device) => device.id === selectedId);
  const tableTags = useMemo(
    () => tags.filter((tag) => selectedTagIds.includes(tag.id)),
    [tags, selectedTagIds],
  );

  const columns: ColumnsType<TagView> = [
    {
      title: '点位',
      key: 'name',
      width: 170,
      fixed: 'left',
      render: (_, tag) => (
        <div>
          <div style={{ fontWeight: 650 }}>{tag.name}</div>
          <div className="tiny-muted" style={{ marginTop: 2 }}>
            {tag.unit || tag.description || '—'}
          </div>
        </div>
      ),
    },
    {
      title: 'NodeId',
      dataIndex: 'nodeId',
      key: 'nodeId',
      width: 190,
      render: (value) => <span className="node-id">{value}</span>,
    },
    {
      title: '类型',
      key: 'type',
      width: 100,
      render: (_, tag) => <DataTypeTag type={tag.dataType} array={tag.array} />,
    },
    {
      title: '实时值',
      key: 'value',
      width: 170,
      render: (_, tag) => {
        const value = values[tag.id];
        if (!value) return <span className="tiny-muted">等待数据…</span>;
        return (
          <span className={flashId === tag.id ? 'live-value value-flash' : 'live-value'}>
            {formatLiveValue(tag, value.value)}
            {value.unit ? <span className="tiny-muted"> {value.unit}</span> : null}
          </span>
        );
      },
    },
    {
      title: '质量',
      key: 'quality',
      width: 100,
      render: (_, tag) => <QualityTag quality={values[tag.id]?.quality} />,
    },
    {
      title: '源时间',
      key: 'time',
      width: 175,
      render: (_, tag) => (
        <span className="tiny-muted">{formatTime(values[tag.id]?.sourceTime)}</span>
      ),
    },
    {
      title: '工程换算',
      key: 'scale',
      width: 90,
      render: (_, tag) =>
        tag.scale === 1 && tag.offset === 0 ? (
          <span className="tiny-muted">—</span>
        ) : (
          <span className="mono">×{tag.scale}{tag.offset >= 0 ? '+' : ''}{tag.offset}</span>
        ),
    },
  ];

  return (
    <div className="page-stack">
      <PageHead
        title="实时数据"
        description="通过 OPC UA Subscription / MonitoredItem 接收服务器推送，支持发布周期、采样周期、队列和死区。关闭页面或点击停止即可释放订阅。"
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
            <Button icon={<ReloadOutlined />} onClick={() => selectedId && loadTags(selectedId)}>
              刷新点位
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
            <div className="live-head">
              <div className="toolbar-left" style={{ flex: 1 }}>
                <Select
                  mode="multiple"
                  style={{ minWidth: 360, flex: 1, maxWidth: 680 }}
                  placeholder="选择要监视的点位（可多选）"
                  value={selectedTagIds}
                  onChange={setSelectedTagIds}
                  maxTagCount="responsive"
                  optionFilterProp="label"
                  options={tags.map((tag) => ({
                    value: tag.id,
                    label: `${tag.name} · ${tag.nodeId}`,
                  }))}
                  disabled={state === 'live' || state === 'connecting'}
                />
                {state === 'live' || state === 'connecting' ? (
                  <Button danger icon={<DisconnectOutlined />} onClick={() => stop()}>
                    停止订阅
                  </Button>
                ) : (
                  <Button type="primary" icon={<PlayCircleOutlined />} onClick={start}>
                    启动实时订阅
                  </Button>
                )}
              </div>
              <Space size={12} wrap>
                <div className="live-stat">
                  <WifiOutlined style={{ color: state === 'live' ? '#22c55e' : '#94a3b8', fontSize: 18 }} />
                  <div>
                    <div className="live-stat-value">
                      <span className={`live-dot ${state === 'live' ? 'on' : ''}`} />
                      {state === 'live'
                        ? '接收中'
                        : state === 'connecting'
                          ? '连接中'
                          : state === 'reconnecting'
                            ? '重连中'
                            : state === 'error'
                              ? '异常'
                              : '未启动'}
                    </div>
                    <div className="live-stat-label">
                      {selectedDevice?.endpointUrl || '—'}
                    </div>
                  </div>
                </div>
                <div className="live-stat">
                  <div>
                    <div className="live-stat-value">{selectedTagIds.length}</div>
                    <div className="live-stat-label">订阅点位</div>
                  </div>
                </div>
                <div className="live-stat">
                  <div>
                    <div className="live-stat-value">{eventCount}</div>
                    <div className="live-stat-label">累计更新</div>
                  </div>
                </div>
                <div className="live-stat">
                  <div>
                    <div className="live-stat-value" style={{ fontSize: 14 }}>
                      {lastEventAt || '—'}
                    </div>
                    <div className="live-stat-label">最近更新</div>
                  </div>
                </div>
              </Space>
            </div>
          </Card>

          {selectedDevice && !selectedDevice.connected && state === 'idle' && (
            <Alert
              type="info"
              showIcon
              message="设备当前离线"
              description="启动订阅时会自动建立 OPC UA 会话；如果服务器不可达，错误会以 SSE 事件返回。"
            />
          )}
          {errorMessage && (
            <Alert
              type="error"
              showIcon
              closable
              onClose={() => setErrorMessage(null)}
              message="实时通道错误"
              description={errorMessage}
            />
          )}
          {readyInfo && (
            <Alert
              type="success"
              showIcon
              message={`已连接 ${readyInfo.deviceName || selectedDevice?.name || ''} 的实时通道`}
              description={`订阅点位 ${readyInfo.tagCount ?? selectedTagIds.length} 个${
                readyInfo.time ? ` · ${new Date(readyInfo.time).toLocaleString('zh-CN')}` : ''
              }`}
            />
          )}

          <Card variant="borderless" className="table-card">
            <Table<TagView>
              rowKey="id"
              columns={columns}
              dataSource={tableTags}
              pagination={false}
              scroll={{ x: 995 }}
              locale={{ emptyText: '选择点位后启动实时订阅' }}
            />
          </Card>

          <Alert
            type="info"
            showIcon
            icon={<ThunderboltOutlined />}
            message="订阅机制"
            description="后端为每台设备维护一个 UaSubscription，前端通过 Server-Sent Events 接收标准化后的值。点位配置的采样周期与队列长度会写入 MonitoredItem；死区会生成 DataChangeFilter。"
          />
        </>
      )}
    </div>
  );
}
