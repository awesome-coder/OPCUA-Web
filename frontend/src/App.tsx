import React, { useEffect, useMemo, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import {
  App as AntdApp,
  ConfigProvider,
  Grid,
  Layout,
  Menu,
  Space,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import zhCN from 'antd/locale/zh_CN';
import {
  ApiOutlined,
  AppstoreOutlined,
  CloudServerOutlined,
  CodeOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  DeploymentUnitOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { api } from './api';
import DashboardPage from './pages/DashboardPage';
import DevicesPage from './pages/DevicesPage';
import DeviceDetailPage from './pages/DeviceDetailPage';
import TagsPage from './pages/TagsPage';
import BrowsePage from './pages/BrowsePage';
import LivePage from './pages/LivePage';
import BacnetPage from './pages/BacnetPage';
import SettingsPage from './pages/SettingsPage';
import NotFoundPage from './pages/NotFoundPage';
import { Routes, Route } from 'react-router-dom';

const { Header, Sider, Content } = Layout;
const { Text } = Typography;

const menuItems = [
  { key: '/', icon: <DashboardOutlined />, label: '运行总览' },
  { key: '/devices', icon: <CloudServerOutlined />, label: '设备管理' },
  { key: '/tags', icon: <DatabaseOutlined />, label: '点位配置' },
  { key: '/browse', icon: <DeploymentUnitOutlined />, label: '地址空间浏览' },
  { key: '/live', icon: <ThunderboltOutlined />, label: '实时数据' },
  { key: '/bacnet', icon: <ApiOutlined />, label: 'BACnet 映射' },
  { key: '/settings', icon: <SettingOutlined />, label: '客户端证书' },
];

const pageTitles: Record<string, { title: string; subtitle: string }> = {
  '/': { title: '运行总览', subtitle: 'OPC UA 接入、点位与数据通道状态' },
  '/devices': { title: '设备管理', subtitle: '配置端点、安全策略与身份认证' },
  '/tags': { title: '点位配置', subtitle: '定义 OPC UA 点位、数据类型与 BACnet 映射' },
  '/browse': { title: '地址空间浏览', subtitle: '浏览服务器节点树并扫描变量' },
  '/live': { title: '实时数据', subtitle: '基于 OPC UA Subscription 的实时值监视' },
  '/bacnet': { title: 'BACnet 映射', subtitle: '把 OPC UA 数据点映射为 BACnet 对象' },
  '/settings': { title: '客户端证书', subtitle: '安全连接所需的客户端身份与信任配置' },
};

function titleFor(pathname: string) {
  if (pathname.startsWith('/devices/')) {
    return { title: '设备详情', subtitle: '连接状态、点位与运行操作' };
  }
  return pageTitles[pathname] || pageTitles['/'];
}

export default function App() {
  const location = useLocation();
  const navigate = useNavigate();
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.lg;
  const [collapsed, setCollapsed] = useState(false);
  const [health, setHealth] = useState<{ status: string; connectedDevices: number } | null>(null);

  useEffect(() => {
    setCollapsed(isMobile);
  }, [isMobile]);

  useEffect(() => {
    let active = true;
    const load = () => {
      api.get('/health')
        .then((res) => {
          if (active) setHealth(res.data);
        })
        .catch(() => {
          if (active) setHealth({ status: 'DOWN', connectedDevices: 0 });
        });
    };
    load();
    const timer = window.setInterval(load, 30_000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, []);

  const selectedKey = useMemo(() => {
    const match = menuItems
      .filter((item) => item.key !== '/' && location.pathname.startsWith(item.key))
      .sort((a, b) => b.key.length - a.key.length)[0];
    return match?.key || '/';
  }, [location.pathname]);

  const page = titleFor(location.pathname);

  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#1677ff',
          colorInfo: '#1677ff',
          colorSuccess: '#16a34a',
          colorWarning: '#d97706',
          colorError: '#dc2626',
          borderRadius: 10,
          fontFamily:
            "'Inter', 'PingFang SC', 'Microsoft YaHei', 'Segoe UI', system-ui, -apple-system, sans-serif",
          colorBgLayout: '#f3f6fb',
        },
        components: {
          Layout: { headerBg: '#ffffff', siderBg: '#0b1324' },
          Menu: {
            darkItemBg: '#0b1324',
            darkSubMenuItemBg: '#0b1324',
            darkItemSelectedBg: '#1d4ed8',
            darkItemHoverBg: '#17233d',
          },
          Card: { boxShadow: '0 10px 30px rgba(15, 23, 42, 0.06)' },
          Table: { headerBg: '#f8fafc' },
        },
      }}
    >
      <AntdApp>
        <Layout className="app-shell">
          {isMobile && !collapsed && (
            <div className="mobile-backdrop" onClick={() => setCollapsed(true)} />
          )}
          <Sider
            className="app-sider"
            width={238}
            collapsedWidth={isMobile ? 0 : 78}
            collapsible
            collapsed={collapsed}
            trigger={null}
            theme="dark"
          >
            <Link to="/" className="brand" onClick={() => isMobile && setCollapsed(true)}>
              <div className="brand-mark">
                <CodeOutlined />
              </div>
              {!collapsed && (
                <div className="brand-copy">
                  <div className="brand-title">OPC UA Console</div>
                  <div className="brand-subtitle">工业数据接入控制台</div>
                </div>
              )}
            </Link>
            <div className="sider-section-label">{!collapsed ? '配置与监视' : ''}</div>
            <Menu
              theme="dark"
              mode="inline"
              selectedKeys={[selectedKey]}
              items={menuItems}
              onClick={({ key }) => {
                navigate(key);
                if (isMobile) setCollapsed(true);
              }}
            />
            <div className="sider-footer">
              {!collapsed && (
                <>
                  <div className="sider-footer-title">
                    <SafetyCertificateOutlined /> 安全提示
                  </div>
                  <div className="sider-footer-text">
                    生产环境建议使用 Basic256Sha256 + SignAndEncrypt，并固定服务器证书。
                  </div>
                </>
              )}
            </div>
          </Sider>
          <Layout>
            <Header className="app-header">
              <Space size={14} align="center">
                <button
                  type="button"
                  className="menu-toggle"
                  onClick={() => setCollapsed((value) => !value)}
                  aria-label="切换菜单"
                >
                  {collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                </button>
                <div>
                  <div className="header-title">{page.title}</div>
                  <div className="header-subtitle">{page.subtitle}</div>
                </div>
              </Space>
              <Space size={18} className="header-right">
                <Tooltip title="后端服务与 OPC UA 会话状态">
                  <div className="service-status">
                    <span className={`status-dot ${health?.status === 'UP' ? 'online' : 'offline'}`} />
                    <div>
                      <div className="service-status-title">
                        {health?.status === 'UP' ? '服务运行中' : '服务不可用'}
                      </div>
                      <div className="service-status-sub">
                        {health?.connectedDevices ?? 0} 个设备会话在线
                      </div>
                    </div>
                  </div>
                </Tooltip>
                <Tag className="version-tag" bordered={false}>
                  v0.1.0
                </Tag>
              </Space>
            </Header>
            <Content className="app-content">
              <Routes>
                <Route path="/" element={<DashboardPage />} />
                <Route path="/devices" element={<DevicesPage />} />
                <Route path="/devices/:id" element={<DeviceDetailPage />} />
                <Route path="/tags" element={<TagsPage />} />
                <Route path="/browse" element={<BrowsePage />} />
                <Route path="/live" element={<LivePage />} />
                <Route path="/bacnet" element={<BacnetPage />} />
                <Route path="/settings" element={<SettingsPage />} />
                <Route path="*" element={<NotFoundPage />} />
              </Routes>
            </Content>
            <footer className="app-footer">
              <Space split={<span className="footer-sep">·</span>}>
                <Text type="secondary">Eclipse Milo {health ? '0.6.16' : ''}</Text>
                <Text type="secondary">Spring Boot 3.5</Text>
                <Text type="secondary">React + Ant Design</Text>
              </Space>
            </footer>
          </Layout>
        </Layout>
      </AntdApp>
    </ConfigProvider>
  );
}
