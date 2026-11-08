export type SecurityPolicy =
  | 'NONE'
  | 'BASIC128RSA15'
  | 'BASIC256'
  | 'BASIC256SHA256'
  | 'AES128_SHA256_RSAOAEP'
  | 'AES256_SHA256_RSAPSS';

export type SecurityMode = 'NONE' | 'SIGN' | 'SIGN_AND_ENCRYPT';
export type AuthMode = 'ANONYMOUS' | 'USERNAME' | 'CERTIFICATE';
export type AccessMode = 'READ_ONLY' | 'READ_WRITE' | 'WRITE_ONLY';
export type DataType =
  | 'AUTO'
  | 'BOOLEAN'
  | 'SBYTE'
  | 'BYTE'
  | 'INT16'
  | 'UINT16'
  | 'INT32'
  | 'UINT32'
  | 'INT64'
  | 'UINT64'
  | 'FLOAT'
  | 'DOUBLE'
  | 'STRING'
  | 'DATETIME'
  | 'GUID'
  | 'BYTESTRING'
  | 'NODEID'
  | 'QUALIFIED_NAME'
  | 'LOCALIZED_TEXT'
  | 'EXTENSION_OBJECT';

export interface OptionItem {
  value: string;
  label: string;
  description?: string;
}

export interface MetaView {
  securityPolicies: OptionItem[];
  securityModes: OptionItem[];
  authModes: OptionItem[];
  dataTypes: OptionItem[];
  accessModes: OptionItem[];
  bacnetObjectTypes: OptionItem[];
  bacnetProperties: string[];
  applicationVersion: string;
  opcUaStackVersion: string;
}

export interface DeviceView {
  id: string;
  name: string;
  description?: string;
  endpointUrl: string;
  securityPolicy: SecurityPolicy;
  securityMode: SecurityMode;
  authMode: AuthMode;
  username?: string;
  hasPassword: boolean;
  trustAllCertificates: boolean;
  hasServerCertificate: boolean;
  connectTimeoutMs: number;
  requestTimeoutMs: number;
  sessionTimeoutMs: number;
  publishingIntervalMs: number;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
  connected: boolean;
  connectionState: string;
  lastError?: string;
  connectedAt?: string;
  tagCount: number;
}

export interface DeviceRequest {
  name: string;
  endpointUrl: string;
  securityPolicy: SecurityPolicy;
  securityMode: SecurityMode;
  authMode: AuthMode;
  username?: string | null;
  password?: string | null;
  trustAllCertificates: boolean;
  serverCertificatePem?: string | null;
  connectTimeoutMs: number;
  requestTimeoutMs: number;
  sessionTimeoutMs: number;
  publishingIntervalMs: number;
  enabled: boolean;
  description?: string | null;
}

export interface TagView {
  id: string;
  deviceId: string;
  deviceName: string;
  name: string;
  nodeId: string;
  dataType: DataType;
  array: boolean;
  accessMode: AccessMode;
  writable: boolean;
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
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface TagRequest {
  deviceId: string;
  name: string;
  nodeId: string;
  dataType: DataType;
  array: boolean;
  accessMode: AccessMode;
  unit?: string | null;
  description?: string | null;
  scale: number;
  offset: number;
  deadband: number;
  samplingIntervalMs: number;
  queueSize: number;
  enumValues?: string | null;
  bacnetObjectType?: string | null;
  bacnetInstance?: number | null;
  bacnetProperty?: string | null;
  covIncrement?: number | null;
  enabled: boolean;
}

export interface EndpointView {
  endpointUrl: string;
  securityPolicy: string;
  securityMode: string;
  securityLevel: number;
  userTokenTypes: string[];
  serverApplicationName?: string;
  serverApplicationUri?: string;
  serverProductUri?: string;
  certificateFingerprintSha256?: string;
  serverCertificatePem?: string;
  transportProfileUri?: string;
}

export interface ConnectionTestResult {
  success: boolean;
  message: string;
  elapsedMs: number;
  endpointUrl: string;
  securityPolicy?: string;
  securityMode?: string;
  authMode?: string;
  serverApplicationName?: string;
  serverApplicationUri?: string;
  endpoints: EndpointView[];
}

export interface DeviceStatusView {
  deviceId: string;
  state: string;
  connected: boolean;
  endpointUrl?: string;
  activeSecurityPolicy?: string;
  activeSecurityMode?: string;
  serverApplicationName?: string;
  connectedAt?: string;
  lastError?: string;
  subscriptionCount: number;
}

export interface BrowseNodeView {
  nodeId: string;
  browseName: string;
  displayName: string;
  nodeClass: string;
  dataType?: string;
  dataTypeName?: string;
  dataTypeCode?: DataType;
  valueRank?: number;
  array: boolean;
  accessLevel?: number;
  writable: boolean;
  description?: string;
  typeDefinition?: string;
  hasChildren: boolean;
  path: string;
}

export interface BrowseResultView {
  parentNodeId: string;
  parentDisplayName: string;
  nodes: BrowseNodeView[];
}

export interface ScanResultView {
  rootNodeId: string;
  scannedObjects: number;
  scannedVariables: number;
  truncated: boolean;
  elapsedMs: number;
  tags: BrowseNodeView[];
  warnings: string[];
}

export interface TagValueView {
  tagId: string;
  tagName: string;
  nodeId: string;
  value: unknown;
  rawValue: unknown;
  dataType: string;
  array: boolean;
  quality: 'GOOD' | 'BAD' | 'UNCERTAIN' | string;
  statusCode: number;
  statusCodeName: string;
  sourceTime?: string;
  serverTime?: string;
  unit?: string;
  scaled: boolean;
  error?: string;
}

export interface NodeValueView {
  nodeId: string;
  value: unknown;
  dataType: string;
  array: boolean;
  quality: string;
  statusCode: number;
  statusCodeName: string;
  sourceTime?: string;
  serverTime?: string;
  error?: string;
}

export interface ReadResultView {
  deviceId: string;
  tags: TagValueView[];
  nodes: NodeValueView[];
  error?: string;
}

export interface WriteResult {
  success: boolean;
  nodeId: string;
  tagId?: string;
  value: unknown;
  quality: string;
  statusCode: number;
  statusCodeName: string;
  message: string;
}

export interface LiveStatusView {
  deviceId: string;
  active: boolean;
  emitters: number;
  monitoredTags: number;
  publishingInterval?: string;
  tagIds: string[];
  lastError?: string;
}

export interface BacnetObjectView {
  tagId: string;
  tagName: string;
  nodeId: string;
  dataType: string;
  array: boolean;
  accessMode: string;
  objectType: string;
  objectTypeNumber: number;
  objectTypeLabel: string;
  instanceNumber: number;
  objectId: string;
  property: string;
  objectName: string;
  description?: string;
  units?: string;
  covIncrement?: number;
  presentValue?: unknown;
  mappingSource: string;
  warnings: string[];
}

export interface BacnetMappingView {
  deviceId: string;
  deviceName: string;
  objectCount: number;
  objects: BacnetObjectView[];
  warnings: string[];
  integrationStatus: string;
}

export interface DashboardView {
  deviceCount: number;
  enabledDeviceCount: number;
  connectedDeviceCount: number;
  tagCount: number;
  subscriptionTagCount: number;
  secureDeviceCount: number;
  bacnetMappedCount: number;
  devices: DeviceView[];
  clientCertificateFingerprint: string;
}

export interface PkiInfoView {
  subject: string;
  issuer: string;
  serialNumber: string;
  notBefore: string;
  notAfter: string;
  applicationUri: string;
  sha256Fingerprint: string;
  sha1Thumbprint: string;
  keyAlgorithm: string;
  keySize: number;
  certificatePem: string;
}

export interface ExcelImportError {
  sheet: string;
  row: number;
  column: string;
  message: string;
}

export interface ExcelImportResult {
  devicesCreated: number;
  devicesUpdated: number;
  devicesSkipped: number;
  tagsCreated: number;
  tagsUpdated: number;
  tagsSkipped: number;
  errorCount: number;
  errors: ExcelImportError[];
  message: string;
}

export interface SystemInfo {
  dataDir: string;
  configFile: string;
  pkiDir?: string;
  corsOrigins: string;
  clientCertificateFingerprint: string;
  liveHeartbeatMs: number;
}
