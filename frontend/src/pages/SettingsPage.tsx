import React, { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Collapse,
  Descriptions,
  Row,
  Space,
  Tag,
  Typography,
  message,
} from 'antd';
import {
  CopyOutlined,
  DownloadOutlined,
  FileProtectOutlined,
  KeyOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import type { PkiInfoView, SystemInfo } from '../types';
import { api, errorText } from '../api';
import PageHead from '../components/PageHead';

const { Paragraph, Text } = Typography;

export default function SettingsPage() {
  const [pki, setPki] = useState<PkiInfoView | null>(null);
  const [system, setSystem] = useState<SystemInfo | null>(null);
  const [loading, setLoading] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const [pkiResponse, systemResponse] = await Promise.all([
        api.get<PkiInfoView>('/pki/info'),
        api.get<SystemInfo>('/system'),
      ]);
      setPki(pkiResponse.data);
      setSystem(systemResponse.data);
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  return (
    <div className="page-stack">
      <PageHead
        title="客户端证书与安全"
        description="OPC UA 安全连接使用应用实例证书建立 SecureChannel。将本控制台的客户端证书加入服务器信任列表后，即可使用签名/加密端点和 X509 用户身份。"
        extra={
          <Space>
            <Button
              icon={<DownloadOutlined />}
              onClick={() => window.open('/api/pki/client-certificate', '_blank')}
            >
              下载客户端证书
            </Button>
            <Button type="primary" icon={<SettingOutlined />} onClick={load} loading={loading}>
              刷新信息
            </Button>
          </Space>
        }
      />

      <Row gutter={16}>
        <Col xs={24} lg={16}>
          <Card
            variant="borderless"
            title={
              <Space>
                <SafetyCertificateOutlined style={{ color: '#1677ff' }} />
                <span>应用实例证书</span>
              </Space>
            }
            loading={loading}
          >
            {pki ? (
              <>
                <div className="cert-fingerprint">{pki.sha256Fingerprint}</div>
                <div style={{ marginTop: 10, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                  <Button
                    size="small"
                    icon={<CopyOutlined />}
                    onClick={() => {
                      navigator.clipboard.writeText(pki.sha256Fingerprint);
                      message.success('SHA-256 指纹已复制');
                    }}
                  >
                    复制指纹
                  </Button>
                  <Button
                    size="small"
                    icon={<DownloadOutlined />}
                    onClick={() => window.open('/api/pki/client-certificate', '_blank')}
                  >
                    下载 PEM
                  </Button>
                  <Tag color="green">有效期至 {new Date(pki.notAfter).toLocaleDateString('zh-CN')}</Tag>
                </div>
                <Descriptions column={2} size="small" bordered style={{ marginTop: 18 }}>
                  <Descriptions.Item label="Subject" span={2}>
                    <span className="mono">{pki.subject}</span>
                  </Descriptions.Item>
                  <Descriptions.Item label="Application URI" span={2}>
                    <span className="mono">{pki.applicationUri}</span>
                  </Descriptions.Item>
                  <Descriptions.Item label="序列号">
                    <span className="mono">{pki.serialNumber}</span>
                  </Descriptions.Item>
                  <Descriptions.Item label="密钥">
                    {pki.keyAlgorithm} {pki.keySize} bit
                  </Descriptions.Item>
                  <Descriptions.Item label="生效时间">
                    {new Date(pki.notBefore).toLocaleString('zh-CN')}
                  </Descriptions.Item>
                  <Descriptions.Item label="过期时间">
                    {new Date(pki.notAfter).toLocaleString('zh-CN')}
                  </Descriptions.Item>
                  <Descriptions.Item label="SHA-1" span={2}>
                    <span className="mono" style={{ fontSize: 10 }}>
                      {pki.sha1Thumbprint}
                    </span>
                  </Descriptions.Item>
                </Descriptions>
              </>
            ) : (
              <Text type="secondary">未获取到证书信息</Text>
            )}
          </Card>
        </Col>
        <Col xs={24} lg={8}>
          <Card variant="borderless" title="启用安全端点的步骤" loading={loading}>
            <div className="flow">
              {[
                ['下载客户端证书', '在设备表单或本页下载 client-cert.pem。'],
                ['导入服务器信任列表', '将证书加入 OPC UA 服务器的 Trusted/Clients 目录。'],
                ['配置安全策略', '选择 Basic256Sha256 与 SignAndEncrypt。'],
                ['固定服务器证书', '关闭“信任所有证书”，从测试端点采纳服务器证书。'],
              ].map(([title, desc], index) => (
                <div className="flow-step" key={title}>
                  <div className="flow-num">{index + 1}</div>
                  <div>
                    <div className="flow-title">{title}</div>
                    <div className="flow-desc">{desc}</div>
                  </div>
                </div>
              ))}
            </div>
          </Card>
        </Col>
      </Row>

      <Row gutter={16}>
        <Col xs={24} lg={12}>
          <Card variant="borderless" title="安全策略说明">
            <Space direction="vertical" size={14} style={{ width: '100%' }}>
              <Alert
                type="success"
                showIcon
                message="推荐：Basic256Sha256 + SignAndEncrypt"
                description="现代 OPC UA 服务器普遍支持，兼顾兼容性与安全强度。"
              />
              <Alert
                type="warning"
                showIcon
                message="兼容：Basic128Rsa15 / Basic256"
                description="使用 SHA-1 等旧算法，仅在老设备不支持新策略时使用。"
              />
              <Alert
                type="error"
                showIcon
                message="调试：SecurityPolicy=None"
                description="不签名、不加密，用户名密码会以明文传输。不要用于生产网络。"
              />
            </Space>
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card variant="borderless" title="运行与存储">
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="数据目录">
                <span className="mono">{system?.dataDir || '—'}</span>
              </Descriptions.Item>
              <Descriptions.Item label="配置文件">
                <span className="mono">{system?.configFile || '—'}</span>
              </Descriptions.Item>
              <Descriptions.Item label="PKI 目录">
                <span className="mono">{system?.pkiDir || '—'}</span>
              </Descriptions.Item>
              <Descriptions.Item label="CORS 来源">
                <span className="mono">{system?.corsOrigins || '—'}</span>
              </Descriptions.Item>
              <Descriptions.Item label="SSE 心跳">
                {system?.liveHeartbeatMs ? `${system.liveHeartbeatMs} ms` : '—'}
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>
      </Row>

      <Card
        variant="borderless"
        title={
          <Space>
            <FileProtectOutlined />
            <span>安全边界与凭据</span>
          </Space>
        }
      >
        <Paragraph style={{ marginBottom: 12, color: '#475569', fontSize: 12, lineHeight: 1.8 }}>
          设备密码使用 AES-GCM 加密后保存在本地 <span className="mono">config.json</span>，密钥位于
          <span className="mono"> secret.key</span>。密码不会通过 REST 回显，也不会导出到 Excel。
          客户端证书和私钥位于 <span className="mono">data/pki</span>。请妥善保护数据目录。
        </Paragraph>
        <Collapse
          ghost
          items={[
            {
              key: 'pem',
              label: '查看客户端证书 PEM',
              children: <div className="pem-box">{pki?.certificatePem || '—'}</div>,
            },
          ]}
        />
      </Card>

      <Alert
        type="info"
        showIcon
        icon={<KeyOutlined />}
        message="证书信任模式"
        description="默认开启“信任所有服务器证书”仅用于调试。生产环境应关闭该选项并固定服务器证书；Milo 客户端会用配置的证书 SHA-256 指纹校验服务器身份。"
      />
    </div>
  );
}
