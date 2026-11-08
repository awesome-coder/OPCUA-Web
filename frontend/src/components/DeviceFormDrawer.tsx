import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Col,
  Divider,
  Drawer,
  Form,
  Input,
  InputNumber,
  Radio,
  Row,
  Select,
  Space,
  Switch,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import {
  ApiOutlined,
  CheckCircleFilled,
  CloseCircleFilled,
  DownloadOutlined,
  ExperimentOutlined,
  LockOutlined,
  SafetyCertificateOutlined,
  SaveOutlined,
} from '@ant-design/icons';
import type {
  AuthMode,
  ConnectionTestResult,
  DeviceRequest,
  DeviceView,
  MetaView,
  SecurityMode,
  SecurityPolicy,
} from '../types';
import { api, errorText } from '../api';
import { SecurityModeTag, SecurityTag } from './StatusTags';

const { Text } = Typography;

interface DeviceFormValues {
  name: string;
  endpointUrl: string;
  description?: string;
  enabled: boolean;
  securityPolicy: SecurityPolicy;
  securityMode: SecurityMode;
  authMode: AuthMode;
  username?: string;
  password?: string;
  trustAllCertificates: boolean;
  serverCertificatePem?: string;
  connectTimeoutMs: number;
  requestTimeoutMs: number;
  sessionTimeoutMs: number;
  publishingIntervalMs: number;
}

const defaults: DeviceFormValues = {
  name: '',
  endpointUrl: 'opc.tcp://',
  description: '',
  enabled: true,
  securityPolicy: 'NONE',
  securityMode: 'NONE',
  authMode: 'ANONYMOUS',
  username: '',
  password: '',
  trustAllCertificates: true,
  serverCertificatePem: '',
  connectTimeoutMs: 10000,
  requestTimeoutMs: 15000,
  sessionTimeoutMs: 120000,
  publishingIntervalMs: 1000,
};

export default function DeviceFormDrawer({
  open,
  editing,
  meta,
  onClose,
  onSaved,
}: {
  open: boolean;
  editing?: DeviceView | null;
  meta?: MetaView | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<DeviceFormValues>();
  const [testing, setTesting] = useState(false);
  const [saving, setSaving] = useState(false);
  const [testResult, setTestResult] = useState<ConnectionTestResult | null>(null);
  const policy = Form.useWatch('securityPolicy', form);
  const authMode = Form.useWatch('authMode', form);
  const trustAll = Form.useWatch('trustAllCertificates', form);
  const mode = Form.useWatch('securityMode', form);
  const serverCertificatePem = Form.useWatch('serverCertificatePem', form);

  useEffect(() => {
    if (!open) return;
    setTestResult(null);
    if (editing) {
      form.setFieldsValue({
        ...defaults,
        name: editing.name,
        endpointUrl: editing.endpointUrl,
        description: editing.description || '',
        enabled: editing.enabled,
        securityPolicy: editing.securityPolicy,
        securityMode: editing.securityMode,
        authMode: editing.authMode,
        username: editing.username || '',
        password: '',
        trustAllCertificates: editing.trustAllCertificates,
        serverCertificatePem: '',
        connectTimeoutMs: editing.connectTimeoutMs,
        requestTimeoutMs: editing.requestTimeoutMs,
        sessionTimeoutMs: editing.sessionTimeoutMs,
        publishingIntervalMs: editing.publishingIntervalMs,
      });
    } else {
      form.resetFields();
      form.setFieldsValue(defaults);
    }
  }, [open, editing, form]);

  useEffect(() => {
    if (!open) return;
    if (policy === 'NONE') {
      if (mode !== 'NONE') form.setFieldValue('securityMode', 'NONE');
    } else if (!mode || mode === 'NONE') {
      form.setFieldValue('securityMode', 'SIGN_AND_ENCRYPT');
    }
  }, [policy, mode, open, form]);

  const selectedPolicy = useMemo(
    () => meta?.securityPolicies.find((item) => item.value === policy),
    [meta, policy],
  );

  const buildPayload = (values: DeviceFormValues): DeviceRequest => ({
    name: values.name?.trim(),
    endpointUrl: values.endpointUrl?.trim(),
    securityPolicy: values.securityPolicy,
    securityMode: values.securityMode,
    authMode: values.authMode,
    username: values.authMode === 'USERNAME' ? values.username?.trim() : null,
    password: values.password ? values.password : null,
    trustAllCertificates: values.trustAllCertificates,
    serverCertificatePem: values.serverCertificatePem?.trim() || null,
    connectTimeoutMs: values.connectTimeoutMs,
    requestTimeoutMs: values.requestTimeoutMs,
    sessionTimeoutMs: values.sessionTimeoutMs,
    publishingIntervalMs: values.publishingIntervalMs,
    enabled: values.enabled,
    description: values.description?.trim() || null,
  });

  const runTest = async () => {
    try {
      const values = await form.validateFields();
      setTesting(true);
      setTestResult(null);
      const response = await api.post<ConnectionTestResult>('/devices/test', buildPayload(values), {
        params: editing ? { existingId: editing.id } : undefined,
        timeout: 90_000,
      });
      setTestResult(response.data);
      if (response.data.success) message.success('连接测试成功');
      else message.error(response.data.message);
    } catch (error: any) {
      if (error?.errorFields) return;
      setTestResult({
        success: false,
        message: errorText(error),
        elapsedMs: 0,
        endpointUrl: '',
        endpoints: [],
      });
      message.error(errorText(error));
    } finally {
      setTesting(false);
    }
  };

  const save = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);
      const payload = buildPayload(values);
      if (editing) {
        await api.put(`/devices/${editing.id}`, payload);
        message.success('设备已更新');
      } else {
        await api.post('/devices', payload);
        message.success('设备已创建');
      }
      onSaved();
      onClose();
    } catch (error: any) {
      if (error?.errorFields) return;
      message.error(errorText(error));
    } finally {
      setSaving(false);
    }
  };

  const applyCertificateFromTest = () => {
    const normalize = (value?: string) => (value || '').replace(/_/g, '').toUpperCase();
    const endpoint = testResult?.endpoints.find(
      (item) =>
        item.serverCertificatePem &&
        normalize(item.securityPolicy) === normalize(policy) &&
        normalize(item.securityMode) === normalize(mode),
    );
    if (!endpoint?.serverCertificatePem) {
      message.warning('测试结果中没有可用证书，请先探测端点');
      return;
    }
    form.setFieldValue('serverCertificatePem', endpoint.serverCertificatePem);
    form.setFieldValue('trustAllCertificates', false);
    message.success('已填入服务器证书，并切换到证书固定模式');
  };

  const authOptions: { value: AuthMode; title: string; desc: string }[] = [
    { value: 'ANONYMOUS', title: '匿名', desc: '不需要用户名或密码，适合公开或调试端点。' },
    { value: 'USERNAME', title: '用户名 / 密码', desc: '使用 OPC UA UserNameIdentityToken。' },
    { value: 'CERTIFICATE', title: 'X509 客户端证书', desc: '使用本控制台客户端证书作为用户身份。' },
  ];

  return (
    <Drawer
      title={editing ? `编辑设备 · ${editing.name}` : '新增 OPC UA 设备'}
      open={open}
      onClose={onClose}
      width={740}
      destroyOnHidden
      footer={
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}>
          <Button icon={<ExperimentOutlined />} onClick={runTest} loading={testing}>
            测试连接
          </Button>
          <Space>
            <Button onClick={onClose}>取消</Button>
            <Button type="primary" icon={<SaveOutlined />} onClick={save} loading={saving}>
              保存设备
            </Button>
          </Space>
        </div>
      }
    >
      <Form form={form} layout="vertical" initialValues={defaults}>
        <Tabs
          className="device-form-tabs"
          items={[
            {
              key: 'basic',
              label: '基本配置',
              children: (
                <Row gutter={16}>
                  <Col span={12}>
                    <Form.Item
                      name="name"
                      label="设备名称"
                      rules={[{ required: true, message: '请输入设备名称' }]}
                    >
                      <Input placeholder="例如：一号线 PLC / 锅炉房网关" maxLength={100} />
                    </Form.Item>
                  </Col>
                  <Col span={12}>
                    <Form.Item name="enabled" label="启用设备" valuePropName="checked">
                      <Switch checkedChildren="启用" unCheckedChildren="禁用" />
                    </Form.Item>
                  </Col>
                  <Col span={24}>
                    <Form.Item
                      name="endpointUrl"
                      label="OPC UA 端点地址"
                      rules={[
                        { required: true, message: '请输入端点地址' },
                        {
                          pattern: /^(opc\.tcp|opc\.https|opc\.wss):\/\/.+/,
                          message: '端点必须以 opc.tcp://、opc.https:// 或 opc.wss:// 开头',
                        },
                      ]}
                      extra="推荐使用 opc.tcp://host:4840。SSL/TLS 加密通过 OPC UA SecurityPolicy + SecurityMode 实现，而不是 HTTPS。"
                    >
                      <Input placeholder="opc.tcp://192.168.1.10:4840" />
                    </Form.Item>
                  </Col>
                  <Col span={24}>
                    <Form.Item name="description" label="描述">
                      <Input.TextArea
                        rows={3}
                        maxLength={500}
                        showCount
                        placeholder="设备位置、用途或维护备注"
                      />
                    </Form.Item>
                  </Col>
                </Row>
              ),
            },
            {
              key: 'security',
              label: (
                <span>
                  安全与加密 <LockOutlined />
                </span>
              ),
              children: (
                <div>
                  <Row gutter={16}>
                    <Col span={12}>
                      <Form.Item
                        name="securityPolicy"
                        label="安全策略"
                        rules={[{ required: true }]}
                        extra={selectedPolicy?.description}
                      >
                        <Select
                          options={(meta?.securityPolicies || []).map((item) => ({
                            value: item.value,
                            label: item.label,
                          }))}
                        />
                      </Form.Item>
                    </Col>
                    <Col span={12}>
                      <Form.Item
                        name="securityMode"
                        label="消息安全模式"
                        rules={[{ required: true }]}
                      >
                        <Select
                          options={(meta?.securityModes || []).map((item) => ({
                            value: item.value,
                            label: item.label,
                          }))}
                        />
                      </Form.Item>
                    </Col>
                  </Row>
                  <Space size={8} wrap style={{ marginBottom: 14 }}>
                    <SecurityTag policy={policy || 'NONE'} />
                    <SecurityModeTag mode={mode || 'NONE'} />
                    {policy === 'NONE' && (
                      <Text className="warning-inline">未加密通道仅建议用于隔离网络。</Text>
                    )}
                  </Space>
                  <Divider style={{ margin: '6px 0 18px' }} />
                  <Form.Item
                    name="trustAllCertificates"
                    label="服务器证书信任策略"
                    valuePropName="checked"
                  >
                    <Switch
                      checkedChildren="信任所有证书（调试）"
                      unCheckedChildren="固定服务器证书"
                    />
                  </Form.Item>
                  {trustAll && policy !== 'NONE' && (
                    <Alert
                      type="warning"
                      showIcon
                      style={{ marginBottom: 14 }}
                      message="当前会接受任意服务器证书，存在中间人攻击风险"
                      description="生产环境请关闭此开关，并在下方粘贴服务器证书 PEM，或通过测试结果中的“信任此证书”自动填入。"
                    />
                  )}
                  {!trustAll && policy !== 'NONE' && !serverCertificatePem && !editing?.hasServerCertificate && (
                    <Alert
                      type="error"
                      showIcon
                      style={{ marginBottom: 14 }}
                      message="证书固定模式需要服务器证书"
                      description="请粘贴服务器证书 PEM，或先关闭证书固定进行测试，再从返回的端点证书中采纳。"
                    />
                  )}
                  {!trustAll && policy !== 'NONE' && editing?.hasServerCertificate && !serverCertificatePem && (
                    <Alert
                      type="info"
                      showIcon
                      style={{ marginBottom: 14 }}
                      message="正在使用已保存的服务器证书"
                      description="证书 PEM 不会回显。留空将保留已保存的固定证书；粘贴新证书可替换。"
                    />
                  )}
                  {policy !== 'NONE' && (
                    <Form.Item
                      name="serverCertificatePem"
                      label="服务器证书 PEM（用于固定）"
                      extra="必须是服务器端点返回的 X.509 证书。Milo 会用 SHA-256 指纹校验服务器身份。留空表示保留现有证书。"
                    >
                      <Input.TextArea
                        rows={6}
                        className="mono"
                        placeholder={'-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----'}
                      />
                    </Form.Item>
                  )}
                  {testResult?.endpoints.some((e) => e.serverCertificatePem) && (
                    <Button size="small" onClick={applyCertificateFromTest}>
                      从测试端点填入服务器证书
                    </Button>
                  )}
                </div>
              ),
            },
            {
              key: 'auth',
              label: '身份认证',
              children: (
                <div>
                  <Form.Item name="authMode" label="认证方式" rules={[{ required: true }]}>
                    <Radio.Group style={{ width: '100%' }}>
                      <Space direction="vertical" style={{ width: '100%' }} size={10}>
                        {authOptions.map((option) => (
                          <Radio
                            key={option.value}
                            value={option.value}
                            style={{
                              border: '1px solid #e2e8f0',
                              borderRadius: 10,
                              padding: '11px 14px',
                              width: '100%',
                              background: authMode === option.value ? '#f0f7ff' : '#fff',
                            }}
                          >
                            <div style={{ fontWeight: 600, fontSize: 13 }}>{option.title}</div>
                            <div style={{ color: '#64748b', fontSize: 11, marginTop: 2 }}>
                              {option.desc}
                            </div>
                          </Radio>
                        ))}
                      </Space>
                    </Radio.Group>
                  </Form.Item>
                  {authMode === 'USERNAME' && (
                    <Row gutter={16}>
                      <Col span={12}>
                        <Form.Item
                          name="username"
                          label="用户名"
                          rules={[{ required: true, message: '请输入用户名' }]}
                        >
                          <Input autoComplete="off" placeholder="OPC UA 用户名" />
                        </Form.Item>
                      </Col>
                      <Col span={12}>
                        <Form.Item
                          name="password"
                          label="密码"
                          rules={
                            editing
                              ? []
                              : [{ required: true, message: '请输入密码' }]
                          }
                          extra={editing ? '留空表示不修改现有密码' : undefined}
                        >
                          <Input.Password
                            autoComplete="new-password"
                            placeholder={editing ? '留空则不修改' : 'OPC UA 密码'}
                          />
                        </Form.Item>
                      </Col>
                    </Row>
                  )}
                  {authMode === 'CERTIFICATE' && (
                    <Alert
                      type="info"
                      showIcon
                      icon={<SafetyCertificateOutlined />}
                      message="X509 用户身份"
                      description={
                        <div style={{ fontSize: 12, lineHeight: 1.7 }}>
                          服务器需要信任本控制台的客户端证书。请先下载客户端证书并导入服务器的
                          Trusted/Clients 列表。可在“客户端证书”页面查看指纹和下载 PEM。
                          <div style={{ marginTop: 10 }}>
                            <Button
                              size="small"
                              icon={<DownloadOutlined />}
                              onClick={() => window.open('/api/pki/client-certificate', '_blank')}
                            >
                              下载客户端证书
                            </Button>
                          </div>
                        </div>
                      }
                    />
                  )}
                  {authMode === 'ANONYMOUS' && (
                    <Alert
                      type="success"
                      showIcon
                      message="匿名访问"
                      description="服务器必须提供 Anonymous 用户令牌；若服务器禁用匿名，请改用用户名或证书认证。"
                    />
                  )}
                </div>
              ),
            },
            {
              key: 'advanced',
              label: '高级参数',
              children: (
                <Row gutter={16}>
                  <Col span={12}>
                    <Form.Item name="connectTimeoutMs" label="连接超时 (ms)">
                      <InputNumber min={1000} max={120000} step={1000} style={{ width: '100%' }} />
                    </Form.Item>
                  </Col>
                  <Col span={12}>
                    <Form.Item name="requestTimeoutMs" label="请求超时 (ms)">
                      <InputNumber min={1000} max={120000} step={1000} style={{ width: '100%' }} />
                    </Form.Item>
                  </Col>
                  <Col span={12}>
                    <Form.Item name="sessionTimeoutMs" label="会话超时 (ms)">
                      <InputNumber
                        min={10000}
                        max={3600000}
                        step={10000}
                        style={{ width: '100%' }}
                      />
                    </Form.Item>
                  </Col>
                  <Col span={12}>
                    <Form.Item
                      name="publishingIntervalMs"
                      label="订阅发布周期 (ms)"
                      extra="实时数据订阅的 publishing interval，建议 500–2000ms。"
                    >
                      <InputNumber min={100} max={600000} step={100} style={{ width: '100%' }} />
                    </Form.Item>
                  </Col>
                </Row>
              ),
            },
          ]}
        />
      </Form>

      {testResult && (
        <div className="connection-test">
          <Alert
            type={testResult.success ? 'success' : 'error'}
            showIcon
            icon={testResult.success ? <CheckCircleFilled /> : <CloseCircleFilled />}
            message={
              <Space wrap>
                <b>{testResult.success ? '连接成功' : '连接失败'}</b>
                {testResult.elapsedMs > 0 && (
                  <Tag bordered={false}>{testResult.elapsedMs} ms</Tag>
                )}
                {testResult.endpoints.length > 0 && (
                  <Tag bordered={false} color="blue">
                    {testResult.endpoints.length} 个端点
                  </Tag>
                )}
              </Space>
            }
            description={
              <div style={{ fontSize: 12, lineHeight: 1.8 }}>
                <div>{testResult.message}</div>
                {testResult.success && (
                  <>
                    <div>
                      服务端：{testResult.serverApplicationName || '未知'}
                      {testResult.serverApplicationUri
                        ? ` · ${testResult.serverApplicationUri}`
                        : ''}
                    </div>
                    <div className="mono" style={{ marginTop: 4 }}>
                      {testResult.endpointUrl}
                    </div>
                  </>
                )}
              </div>
            }
          />
        </div>
      )}
    </Drawer>
  );
}
