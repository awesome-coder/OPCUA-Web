import axios, { AxiosError } from 'axios';

export const api = axios.create({
  baseURL: '/api',
  timeout: 60_000,
});

export function errorText(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const axiosError = error as AxiosError<any>;
    const data = axiosError.response?.data;
    if (data?.message) {
      const details = data.details?.errors;
      if (Array.isArray(details) && details.length > 0) {
        return `${data.message}：${details.map((e: any) => e.message).join('；')}`;
      }
      return data.message;
    }
    if (axiosError.code === 'ECONNABORTED') {
      return '请求超时，请检查网络或增加超时时间';
    }
    if (axiosError.message) {
      return axiosError.message;
    }
  }
  if (error instanceof Error) {
    return error.message;
  }
  return '操作失败';
}

export function extractFilename(headers: Record<string, any> | undefined, fallback: string): string {
  const disposition = headers?.['content-disposition'];
  if (!disposition) return fallback;
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
  if (utf8?.[1]) return decodeURIComponent(utf8[1]);
  const plain = /filename="?([^";]+)"?/i.exec(disposition);
  return plain?.[1] || fallback;
}

export async function download(url: string, fallbackName: string): Promise<void> {
  const response = await api.get(url, { responseType: 'blob', timeout: 120_000 });
  const filename = extractFilename(response.headers, fallbackName);
  const href = URL.createObjectURL(response.data);
  const link = document.createElement('a');
  link.href = href;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(href), 3000);
}

export function formatValue(value: unknown): string {
  if (value === null || value === undefined) return '—';
  if (typeof value === 'boolean') return value ? 'true' : 'false';
  if (typeof value === 'number') {
    if (!Number.isFinite(value)) return String(value);
    if (Number.isInteger(value)) return String(value);
    return String(Number(value.toFixed(4)));
  }
  if (typeof value === 'object') {
    try {
      return JSON.stringify(value);
    } catch {
      return String(value);
    }
  }
  return String(value);
}

export function shortNodeId(nodeId?: string): string {
  if (!nodeId) return '—';
  return nodeId.length > 42 ? `${nodeId.slice(0, 20)}…${nodeId.slice(-18)}` : nodeId;
}

export function displayDate(value?: string): string {
  if (!value) return '—';
  return new Date(value).toLocaleString('zh-CN', { hour12: false });
}
