/**
 * HTTP mínimo para os BFFs Conexus: só HTTPS, timeout, sem cookies, corpo limitado a 1 MiB.
 * O corpo da resposta só é usado para ler campos esperados; nunca vai para a tela nem para log.
 */
import { ConexusAccountError } from './errors';

export type FetchLike = (
  url: string,
  init: { method: string; headers: Record<string, string>; body?: string; signal?: AbortSignal; credentials?: 'omit' },
) => Promise<{ status: number; text: () => Promise<string> }>;

export interface HttpResponse {
  status: number;
  json: unknown;
}

export const MAX_RESPONSE_CHARS = 1_048_576;
const TIMEOUT_MS = 15_000;

/** Só HTTPS; `http://` para localhost/emulador/rede local apenas em desenvolvimento (__DEV__). */
export function isAllowedUrl(url: string, dev: boolean = typeof __DEV__ !== 'undefined' && __DEV__): boolean {
  if (url.startsWith('https://')) return true;
  return dev && /^http:\/\/(localhost|127\.0\.0\.1|10\.0\.2\.2|192\.168\.\d+\.\d+)(:\d+)?(\/|$)/.test(url);
}

export interface RequestOptions {
  method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  headers?: Record<string, string>;
  bearer?: string;
  form?: string;
  json?: unknown;
}

export async function httpRequest(fetchFn: FetchLike, url: string, opts: RequestOptions): Promise<HttpResponse> {
  if (!isAllowedUrl(url)) throw new ConexusAccountError('insecure_url');
  const headers: Record<string, string> = { Accept: 'application/json', ...opts.headers };
  let body: string | undefined;
  if (opts.form !== undefined) {
    headers['Content-Type'] = 'application/x-www-form-urlencoded; charset=utf-8';
    body = opts.form;
  } else if (opts.json !== undefined) {
    headers['Content-Type'] = 'application/json; charset=utf-8';
    body = JSON.stringify(opts.json);
  }
  if (opts.bearer) headers.Authorization = `Bearer ${opts.bearer}`;

  const controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
  const timer = controller ? setTimeout(() => controller.abort(), TIMEOUT_MS) : null;
  let status: number;
  let text: string;
  try {
    const res = await fetchFn(url, { method: opts.method, headers, body, signal: controller?.signal, credentials: 'omit' });
    status = res.status;
    text = await res.text();
  } catch {
    throw new ConexusAccountError('network');
  } finally {
    if (timer) clearTimeout(timer);
  }
  if (text.length > MAX_RESPONSE_CHARS) throw new ConexusAccountError('invalid_response');
  let json: unknown = null;
  if (text) {
    try {
      json = JSON.parse(text);
    } catch {
      json = null;
    }
  }
  return { status, json };
}

export function isOk(res: HttpResponse): boolean {
  return res.status >= 200 && res.status < 300;
}

/** Campo `error` do corpo JSON (código curto), se houver. */
export function errorCode(json: unknown): string | null {
  if (json && typeof json === 'object' && typeof (json as { error?: unknown }).error === 'string') {
    const code = (json as { error: string }).error;
    return /^[a-z_]{1,64}$/.test(code) ? code : null;
  }
  return null;
}
