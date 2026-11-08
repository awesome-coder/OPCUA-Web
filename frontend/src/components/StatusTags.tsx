import React from 'react';
import { Tag, Tooltip } from 'antd';
import {
  CheckCircleFilled,
  CloseCircleFilled,
  ExclamationCircleFilled,
  LockOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import type { AccessMode, DataType, SecurityMode, SecurityPolicy } from '../types';

export const securityLabels: Record<string, string> = {
  NONE: 'None',
  BASIC128RSA15: 'Basic128Rsa15',
  BASIC256: 'Basic256',
  BASIC256SHA256: 'Basic256Sha256',
  AES128_SHA256_RSAOAEP: 'Aes128_Sha256_RsaOaep',
  AES256_SHA256_RSAPSS: 'Aes256_Sha256_RsaPss',
};

export function SecurityTag({ policy }: { policy: SecurityPolicy | string }) {
  if (policy === 'NONE') {
    return (
      <Tag icon={<ExclamationCircleFilled />} color="warning" style={{ marginInlineEnd: 0 }}>
        未加密
      </Tag>
    );
  }
  return (
    <Tag icon={<LockOutlined />} color="blue" style={{ marginInlineEnd: 0 }}>
      {securityLabels[policy] || policy}
    </Tag>
  );
}

export function ConnectionTag({
  connected,
  state,
  error,
}: {
  connected: boolean;
  state?: string;
  error?: string;
}) {
  if (connected) {
    return (
      <Tag icon={<CheckCircleFilled />} color="success" style={{ marginInlineEnd: 0 }}>
        在线
      </Tag>
    );
  }
  if (state === 'ERROR') {
    return (
      <Tooltip title={error || '连接失败'}>
        <Tag icon={<CloseCircleFilled />} color="error" style={{ marginInlineEnd: 0 }}>
          异常
        </Tag>
      </Tooltip>
    );
  }
  return (
    <Tag color="default" style={{ marginInlineEnd: 0 }}>
      离线
    </Tag>
  );
}

export function AccessTag({ mode }: { mode: AccessMode | string }) {
  if (mode === 'READ_WRITE') return <Tag color="geekblue">读写</Tag>;
  if (mode === 'WRITE_ONLY') return <Tag color="purple">只写</Tag>;
  return <Tag color="default">只读</Tag>;
}

export function DataTypeTag({ type, array }: { type: DataType | string; array?: boolean }) {
  const color =
    type === 'BOOLEAN'
      ? 'gold'
      : type === 'STRING'
        ? 'purple'
        : type === 'AUTO'
          ? 'default'
          : type === 'EXTENSION_OBJECT'
            ? 'volcano'
            : 'cyan';
  return (
    <Tag color={color} className="mono" style={{ marginInlineEnd: 0 }}>
      {type}
      {array ? '[]' : ''}
    </Tag>
  );
}

export function QualityTag({ quality }: { quality?: string }) {
  if (quality === 'GOOD') return <Tag color="success">GOOD</Tag>;
  if (quality === 'UNCERTAIN') return <Tag color="warning">UNCERTAIN</Tag>;
  return <Tag color="error">{quality || 'UNKNOWN'}</Tag>;
}

export function SecurityModeTag({ mode }: { mode: SecurityMode | string }) {
  if (mode === 'NONE') return <Tag>None</Tag>;
  if (mode === 'SIGN') return <Tag color="cyan">Sign</Tag>;
  return (
    <Tag icon={<SafetyCertificateOutlined />} color="success">
      SignAndEncrypt
    </Tag>
  );
}

export function authLabel(mode: string): string {
  if (mode === 'USERNAME') return '用户名 / 密码';
  if (mode === 'CERTIFICATE') return '客户端证书';
  return '匿名';
}
