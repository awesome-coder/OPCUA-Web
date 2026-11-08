import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Col,
  Collapse,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Switch,
  Tabs,
  Tag,
  message,
} from 'antd';
import {
  ApartmentOutlined,
  CloudDownloadOutlined,
  InfoCircleOutlined,
  SaveOutlined,
} from '@ant-design/icons';
import type {
  AccessMode,
  BrowseNodeView,
  DataType,
  MetaView,
  TagRequest,
  TagView,
} from '../types';
import { api, errorText } from '../api';
import { AccessTag, DataTypeTag } from './StatusTags';

interface TagFormValues {
  name: string;
  nodeId: string;
  dataType: DataType;
  array: boolean;
  accessMode: AccessMode;
  enabled: boolean;
  unit?: string;
  description?: string;
  scale: number;
  offset: number;
  deadband: number;
  samplingIntervalMs: number;
  queueSize: number;
  enumValues?: string;
  bacnetObjectType?: string;
  bacnetInstance?: number;
  bacnetProperty?: string;
  covIncrement?: number;
}

const defaults: TagFormValues = {
  name: '',
  nodeId: 'ns=2;s=',
  dataType: 'AUTO',
  array: false,
  accessMode: 'READ_ONLY',
  enabled: true,
  unit: '',
  description: '',
  scale: 1,
  offset: 0,
  deadband: 0,
  samplingIntervalMs: 0,
  queueSize: 1,
  enumValues: '',
  bacnetObjectType: undefined,
  bacnetInstance: undefined,
  bacnetProperty: 'present-value',
  covIncrement: undefined,
};

export default function TagFormModal({
  open,
  deviceId,
  editing,
  initialNode,
  meta,
  onClose,
  onSaved,
}: {
  open: boolean;
  deviceId?: string;
  editing?: TagView | null;
  initialNode?: BrowseNodeView | null;
  meta?: MetaView | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<TagFormValues>();
  const [saving, setSaving] = useState(false);
  const [inspecting, setInspecting] = useState(false);
  const dataType = Form.useWatch('dataType', form);
  const array = Form.useWatch('array', form);
  const accessMode = Form.useWatch('accessMode', form);

  useEffect(() => {
    if (!open) return;
    if (editing) {
      form.setFieldsValue({
        ...defaults,
        name: editing.name,
        nodeId: editing.nodeId,
        dataType: editing.dataType,
        array: editing.array,
        accessMode: editing.accessMode,
        enabled: editing.enabled,
        unit: editing.unit || '',
        description: editing.description || '',
        scale: editing.scale,
        offset: editing.offset,
        deadband: editing.deadband,
        samplingIntervalMs: editing.samplingIntervalMs,
        queueSize: editing.queueSize,
        enumValues: editing.enumValues || '',
        bacnetObjectType: editing.bacnetObjectType || undefined,
        bacnetInstance: editing.bacnetInstance,
        bacnetProperty: editing.bacnetProperty || 'present-value',
        covIncrement: editing.covIncrement,
      });
    } else {
      form.resetFields();
      form.setFieldsValue({
        ...defaults,
        name: initialNode?.displayName || '',
        nodeId: initialNode?.nodeId || 'ns=2;s=',
        dataType: initialNode?.dataTypeCode || 'AUTO',
        array: initialNode?.array || false,
        accessMode: initialNode?.writable ? 'READ_WRITE' : 'READ_ONLY',
        description: initialNode?.description || '',
      });
    }
  }, [open, editing, initialNode, form]);

  const inspectNode = async () => {
    if (!deviceId) {
      message.warning('请先选择设备');
      return;
    }
    const nodeId = form.getFieldValue('nodeId');
    if (!nodeId) {
      message.warning('请先填写 NodeId');
      return;
    }
    try {
      setInspecting(true);
      const response = await api.get<BrowseNodeView>(`/devices/${deviceId}/inspect`, {
        params: { nodeId },
      });
      const node = response.data;
      form.setFieldsValue({
        name: form.getFieldValue('name') || node.displayName,
        dataType: node.dataTypeCode || form.getFieldValue('dataType'),
        array: node.array,
        accessMode: node.writable ? 'READ_WRITE' : form.getFieldValue('accessMode'),
        description: form.getFieldValue('description') || node.description || '',
      });
      message.success(`已读取节点属性：${node.dataTypeName || '未知类型'}`);
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setInspecting(false);
    }
  };

  const save = async () => {
    if (!deviceId) {
      message.warning('请先选择设备');
      return;
    }
    try {
      const values = await form.validateFields();
      setSaving(true);
      const payload: TagRequest = {
        deviceId,
        name: values.name.trim(),
        nodeId: values.nodeId.trim(),
        dataType: values.dataType,
        array: values.array,
        accessMode: values.accessMode,
        unit: values.unit?.trim() || null,
        description: values.description?.trim() || null,
        scale: values.scale,
        offset: values.offset,
        deadband: values.deadband,
        samplingIntervalMs: values.samplingIntervalMs,
        queueSize: values.queueSize,
        enumValues: values.enumValues?.trim() || null,
        bacnetObjectType: values.bacnetObjectType || null,
        bacnetInstance: values.bacnetInstance ?? null,
        bacnetProperty: values.bacnetProperty || 'present-value',
        covIncrement: values.covIncrement ?? null,
        enabled: values.enabled,
      };
      if (editing) {
        await api.put(`/tags/${editing.id}`, payload);
        message.success('点位已更新');
      } else {
        await api.post('/tags', payload);
        message.success('点位已创建');
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

  const dataTypeOptions = useMemo(
    () => (meta?.dataTypes || []).map((item) => ({ value: item.value, label: item.label })),
    [meta],
  );

  return (
    <Modal
      title={editing ? `编辑点位 · ${editing.name}` : '新增 OPC UA 点位'}
      open={open}
      onCancel={onClose}
      width={800}
      destroyOnHidden
      footer={
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}>
          <Space>
            <DataTypeTag type={dataType || 'AUTO'} array={array} />
            <AccessTag mode={accessMode || 'READ_ONLY'} />
          </Space>
          <Space>
            <Button onClick={onClose}>取消</Button>
            <Button type="primary" icon={<SaveOutlined />} onClick={save} loading={saving}>
              保存点位
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
              label: '点位定义',
              children: (
                <Row gutter={16}>
                  <Col span={12}>
                    <Form.Item
                      name="name"
                      label="点位名称"
                      rules={[{ required: true, message: '请输入点位名称' }]}
                    >
                      <Input placeholder="例如：出口温度" maxLength={120} />
                    </Form.Item>
                  </Col>
                  <Col span={12}>
                    <Form.Item
                      name="nodeId"
                      label="OPC UA NodeId"
                      rules={[{ required: true, message: '请输入 NodeId' }]}
                      extra={
                        <Button
                          type="link"
                          size="small"
                          icon={<CloudDownloadOutlined />}
                          loading={inspecting}
                          onClick={inspectNode}
                          style={{ padding: 0, height: 'auto', marginTop: 4 }}
                        >
                          从服务器读取节点属性
                        </Button>
                      }
                    >
                      <Input placeholder="ns=2;s=Demo.Temperature" />
                    </Form.Item>
                  </Col>
                  <Col span={12}>
                    <Form.Item
                      name="dataType"
                      label="数据类型"
                      rules={[{ required: true }]}
                      extra="AUTO 会保留服务器原始类型；已明确类型时也用于写入转换。"
                    >
                      <Select showSearch options={dataTypeOptions} />
                    </Form.Item>
                  </Col>
                  <Col span={6}>
                    <Form.Item name="array" label="数组点位" valuePropName="checked">
                      <Switch checkedChildren="数组" unCheckedChildren="标量" />
                    </Form.Item>
                  </Col>
                  <Col span={6}>
                    <Form.Item name="enabled" label="启用" valuePropName="checked">
                      <Switch />
                    </Form.Item>
                  </Col>
                  <Col span={12}>
                    <Form.Item name="accessMode" label="访问方式" rules={[{ required: true }]}>
                      <Select
                        options={(meta?.accessModes || []).map((item) => ({
                          value: item.value,
                          label: item.label,
                        }))}
                      />
                    </Form.Item>
                  </Col>
                  <Col span={12}>
                    <Form.Item name="unit" label="工程单位">
                      <Input placeholder="°C、kPa、rpm..." maxLength={60} />
                    </Form.Item>
                  </Col>
                  <Col span={24}>
                    <Form.Item name="description" label="描述">
                      <Input.TextArea rows={2} maxLength={500} showCount />
                    </Form.Item>
                  </Col>
                  <Col span={24}>
                    <Alert
                      type="info"
                      showIcon
                      icon={<InfoCircleOutlined />}
                      message="从地址空间自动填充"
                      description="在“地址空间浏览”页面选择一个变量后点击“添加点位”，名称、NodeId、数据类型和访问方式会自动带入。"
                    />
                  </Col>
                </Row>
              ),
            },
            {
              key: 'conversion',
              label: '数据转换',
              children: (
                <div>
                  <Alert
                    type="info"
                    showIcon
                    style={{ marginBottom: 18 }}
                    message="工程值 = 原始值 × 缩放系数 + 偏移量"
                    description="读取时正向换算，写入时自动反算。缩放系数不能为 0；数组会按元素逐一换算。"
                  />
                  <Row gutter={16}>
                    <Col span={8}>
                      <Form.Item
                        name="scale"
                        label="缩放系数"
                        rules={[
                          { required: true },
                          {
                            validator: (_, value) =>
                              value === 0
                                ? Promise.reject(new Error('缩放系数不能为 0'))
                                : Promise.resolve(),
                          },
                        ]}
                      >
                        <InputNumber step={0.1} style={{ width: '100%' }} />
                      </Form.Item>
                    </Col>
                    <Col span={8}>
                      <Form.Item name="offset" label="偏移量">
                        <InputNumber step={0.1} style={{ width: '100%' }} />
                      </Form.Item>
                    </Col>
                    <Col span={8}>
                      <Form.Item
                        name="deadband"
                        label="订阅死区（绝对值）"
                        extra="大于 0 时创建 DataChangeFilter，仅对数值类型生效。"
                      >
                        <InputNumber min={0} step={0.01} style={{ width: '100%' }} />
                      </Form.Item>
                    </Col>
                    <Col span={24}>
                      <Form.Item
                        name="enumValues"
                        label="枚举值定义（可选）"
                        extra="例如：0=停止;1=运行;2=故障。存在枚举值时，整型点位默认映射为 BACnet Multi-state 对象。"
                      >
                        <Input.TextArea rows={2} placeholder="0=停止;1=运行;2=故障" />
                      </Form.Item>
                    </Col>
                  </Row>
                </div>
              ),
            },
            {
              key: 'subscription',
              label: '订阅参数',
              children: (
                <Row gutter={16}>
                  <Col span={12}>
                    <Form.Item
                      name="samplingIntervalMs"
                      label="采样周期 (ms)"
                      extra="0 表示使用服务器/发布周期（samplingInterval = -1）。"
                    >
                      <InputNumber min={0} max={3600000} step={100} style={{ width: '100%' }} />
                    </Form.Item>
                  </Col>
                  <Col span={12}>
                    <Form.Item name="queueSize" label="队列长度">
                      <InputNumber min={1} max={1000} style={{ width: '100%' }} />
                    </Form.Item>
                  </Col>
                  <Col span={24}>
                    <Alert
                      type="info"
                      showIcon
                      message="订阅通道"
                      description="实时数据页面会以设备发布周期创建 UaSubscription，并为每个点位创建 MonitoredItem。点位修改后订阅会自动重建。"
                    />
                  </Col>
                </Row>
              ),
            },
            {
              key: 'bacnet',
              label: (
                <span>
                  <ApartmentOutlined /> BACnet
                </span>
              ),
              children: (
                <div>
                  <Alert
                    type="info"
                    showIcon
                    style={{ marginBottom: 18 }}
                    message="留空或选择 AUTO 时按数据类型自动映射"
                    description="只读数值 → AnalogInput；读写数值 → AnalogValue；布尔 → BinaryInput/BinaryValue；带枚举的整型 → Multi-state；字符串/数组 → CharacterStringValue。可在此覆盖。"
                  />
                  <Row gutter={16}>
                    <Col span={12}>
                      <Form.Item name="bacnetObjectType" label="BACnet 对象类型（覆盖）">
                        <Select
                          allowClear
                          showSearch
                          placeholder="AUTO / 自动"
                          options={(meta?.bacnetObjectTypes || []).map((item) => ({
                            value: item.value,
                            label: item.label,
                            title: item.description,
                          }))}
                        />
                      </Form.Item>
                    </Col>
                    <Col span={6}>
                      <Form.Item name="bacnetInstance" label="实例号">
                        <InputNumber
                          min={0}
                          max={4194302}
                          style={{ width: '100%' }}
                          placeholder="自动分配"
                        />
                      </Form.Item>
                    </Col>
                    <Col span={6}>
                      <Form.Item name="covIncrement" label="COV 增量">
                        <InputNumber min={0} step={0.1} style={{ width: '100%' }} />
                      </Form.Item>
                    </Col>
                    <Col span={12}>
                      <Form.Item name="bacnetProperty" label="BACnet 属性">
                        <Select
                          showSearch
                          options={(meta?.bacnetProperties || ['present-value']).map((item) => ({
                            value: item,
                            label: item,
                          }))}
                        />
                      </Form.Item>
                    </Col>
                  </Row>
                </div>
              ),
            },
          ]}
        />
      </Form>
    </Modal>
  );
}
