/**
 * Clientes das rotas usadas pelo AntiSpam BR, sobre a sessão de `@conexus/account-expo` (`authorizedRequest`:
 * Bearer, 401 → refresh → nova tentativa única, erros com mensagem fixa):
 * - API BFF (`apiBaseUrl`): `/api/v1/antispam/backup`, `/api/v1/antispam/reports`, `/api/v1/antispam/me`.
 * - Payments BFF: `POST/GET /v1/me/donations` (formato de bff-payment `Api::V1::Me::DonationsController`).
 * Toda resposta é validada campo a campo; nada do corpo vai para a tela além dos campos esperados.
 */
import {
  CONEXUS_ACCOUNT_ERROR_MESSAGES,
  ConexusAccountError,
  isConexusAccountError,
  type ConexusAccountErrorCode,
  type ConexusSession,
} from '@conexus/account-expo';
import type { ReportCategory } from '../core/types';

export const SCHEME = 'antispambr';
export const BILLING_RETURN_URI = `${SCHEME}://billing/return`;
export const PRODUCT_SLUG = 'antispam-br';

// ---------- Erros ----------

/** Erros só do AntiSpam BR (os da conta/HTTP são `ConexusAccountError` do pacote). */
export type AntispamErrorCode = 'checkout_insecure' | 'number_not_supported' | 'backup_invalid' | 'reauth_required';

export const ANTISPAM_ERROR_MESSAGES: Record<AntispamErrorCode, string> = {
  checkout_insecure: 'Link de pagamento recusado (não é HTTPS).',
  number_not_supported: 'Este número não está no formato internacional (+55…) e não pode ser enviado.',
  backup_invalid: 'O backup da nuvem está em formato inválido e não foi aplicado.',
  reauth_required: 'Esta sessão não vale para o AntiSpam BR. Entre novamente.',
};

/** Textos do pacote ajustados ao AntiSpam BR (o app funciona sem conta; limites do servidor). */
const MESSAGE_OVERRIDES: Partial<Record<ConexusAccountErrorCode, string>> = {
  invalid_client: 'O AntiSpam BR ainda não está registrado na Conta Conexus.',
  network: 'Sem conexão com os serviços Conexus. O app continua funcionando sem conta.',
  audience_not_allowed: 'O AntiSpam BR ainda não está autorizado neste serviço Conexus.',
  not_found: 'Nada encontrado na nuvem.',
  payload_too_large: 'Dados grandes demais para enviar (backup: limite de 256 KB).',
  rate_limited: 'Limite de uso atingido. Tente novamente mais tarde (denúncias: até 50 por dia).',
  unavailable: 'Serviço Conexus indisponível no momento. O app continua funcionando sem conta.',
};

export class AntispamError extends Error {
  readonly code: AntispamErrorCode;
  constructor(code: AntispamErrorCode) {
    super(ANTISPAM_ERROR_MESSAGES[code]);
    this.name = 'AntispamError';
    this.code = code;
  }
}

/** Mensagem segura para qualquer erro (nunca expõe corpo do servidor, tokens ou exceções desconhecidas). */
export function antispamErrorMessage(error: unknown): string {
  if (error instanceof AntispamError) return error.message;
  if (error instanceof ConexusAccountError) return MESSAGE_OVERRIDES[error.code] ?? error.message;
  return MESSAGE_OVERRIDES.unavailable ?? CONEXUS_ACCOUNT_ERROR_MESSAGES.unavailable;
}

// ---------- Backup ----------

export interface CloudBackupData {
  blocked: string[];
  allowed: string[];
  rules: unknown[];
  settings: Record<string, unknown>;
}

export interface CloudBackupPayload {
  version: 1;
  device_name?: string;
  data: CloudBackupData;
}

export interface CloudBackup {
  version: number;
  updatedAt: string;
  deviceName: string | null;
  data: unknown;
}

// ---------- Denúncias ----------

export type CloudReportCategory = 'golpe' | 'telemarketing' | 'cobranca' | 'robo' | 'outro';

export const CLOUD_REPORT_CATEGORY_LABELS: Record<CloudReportCategory, string> = {
  golpe: 'Golpe',
  telemarketing: 'Telemarketing',
  cobranca: 'Cobrança',
  robo: 'Robô',
  outro: 'Outro',
};

/** Categorias locais (mais finas) → categorias do servidor. */
export function toCloudCategory(category: ReportCategory): CloudReportCategory {
  switch (category) {
    case 'golpe':
    case 'fraude':
    case 'banco':
      return 'golpe';
    case 'telemarketing':
    case 'spam':
    case 'pesquisa':
    case 'operadora':
      return 'telemarketing';
    case 'cobranca':
      return 'cobranca';
    case 'robocall':
      return 'robo';
    default:
      return 'outro';
  }
}

/** Mesmo padrão do bff-api: `\A\+[1-9]\d{6,14}\z`. */
const E164 = /^\+[1-9]\d{6,14}$/;

/** Remove espaços, hífens, pontos e parênteses; devolve E.164 ou null. */
export function toE164(number: string): string | null {
  const compact = number.trim().replace(/[\s().-]/g, '');
  return E164.test(compact) ? compact : null;
}

export function isE164(number: string): boolean {
  return toE164(number) !== null;
}

export interface CloudReport {
  id: string;
  number: string;
  category: CloudReportCategory;
  note: string | null;
  occurredAt: string | null;
  createdAt: string;
}

export interface CloudReportPage {
  reports: CloudReport[];
  page: number;
  perPage: number;
  total: number;
}

// ---------- Doações ----------

export const DONATION_MIN_CENTS = 100;
export const DONATION_MAX_CENTS = 1_000_000;
export const DONATION_MESSAGE_MAX = 500;

export type DonationStatus = 'pending' | 'paid' | 'failed' | 'expired';

export const DONATION_STATUS_LABELS: Record<DonationStatus, string> = {
  pending: 'pendente',
  paid: 'paga',
  failed: 'falhou',
  expired: 'expirada',
};

export interface DonationCheckout {
  donationId: string;
  checkoutUrl: string;
  provider: string;
}

export interface Donation {
  id: string;
  amountCents: number;
  currency: string;
  status: DonationStatus;
  productSlug: string | null;
  createdAt: string;
}

export function donationBody(amountCents: number, message?: string | null) {
  if (!Number.isInteger(amountCents) || amountCents < DONATION_MIN_CENTS || amountCents > DONATION_MAX_CENTS) {
    throw new ConexusAccountError('validation_failed');
  }
  const note = message?.trim() || undefined;
  if (note && note.length > DONATION_MESSAGE_MAX) throw new ConexusAccountError('validation_failed');
  return {
    amount_cents: amountCents,
    currency: 'BRL',
    product_slug: PRODUCT_SLUG,
    return_url: BILLING_RETURN_URI,
    ...(note ? { message: note } : {}),
  };
}

/** "10", "10,50" ou "10.50" reais → centavos; null se inválido ou fora do limite. */
export function parseReais(input: string): number | null {
  const n = input.trim().replace(',', '.');
  if (!/^\d{1,5}(\.\d{1,2})?$/.test(n)) return null;
  const [int, frac = ''] = n.split('.');
  const cents = Number(int) * 100 + Number((frac + '00').slice(0, 2));
  return cents >= DONATION_MIN_CENTS && cents <= DONATION_MAX_CENTS ? cents : null;
}

export function formatReais(cents: number): string {
  const sign = cents < 0 ? '-' : '';
  const abs = Math.abs(Math.round(cents));
  return `${sign}R$ ${Math.floor(abs / 100)},${String(abs % 100).padStart(2, '0')}`;
}

// ---------- Parsing ----------

function obj(v: unknown): Record<string, unknown> {
  if (!v || typeof v !== 'object' || Array.isArray(v)) throw new ConexusAccountError('invalid_response');
  return v as Record<string, unknown>;
}
function str(v: unknown, max = 200): string {
  if (typeof v !== 'string' || v.length > max) throw new ConexusAccountError('invalid_response');
  return v;
}
function optStr(v: unknown, max = 500): string | null {
  return v === null || v === undefined ? null : str(v, max);
}
function id(v: unknown): string {
  if (typeof v === 'number' && Number.isFinite(v)) return String(v);
  return str(v, 64);
}
function int(v: unknown): number {
  if (typeof v !== 'number' || !Number.isInteger(v) || v < 0) throw new ConexusAccountError('invalid_response');
  return v;
}

export function parseBackup(json: unknown): CloudBackup {
  const b = obj(json);
  return {
    version: int(b.version),
    updatedAt: str(b.updated_at, 64),
    deviceName: optStr(b.device_name, 80),
    data: b.data,
  };
}

const CLOUD_CATEGORIES = Object.keys(CLOUD_REPORT_CATEGORY_LABELS) as CloudReportCategory[];

export function parseReport(json: unknown): CloudReport {
  const r = obj(json);
  const category = str(r.category, 32) as CloudReportCategory;
  return {
    id: id(r.id),
    number: str(r.number, 20),
    category: CLOUD_CATEGORIES.includes(category) ? category : 'outro',
    note: optStr(r.note),
    occurredAt: optStr(r.occurred_at, 64),
    createdAt: str(r.created_at, 64),
  };
}

export function parseReportPage(json: unknown): CloudReportPage {
  const p = obj(json);
  if (!Array.isArray(p.reports)) throw new ConexusAccountError('invalid_response');
  return {
    reports: p.reports.slice(0, 200).map(parseReport),
    page: int(p.page),
    perPage: int(p.per_page),
    total: int(p.total),
  };
}

export function parseCheckout(json: unknown): DonationCheckout {
  const c = obj(json);
  const url = str(c.checkout_url, 4096);
  if (!/^https:\/\/[^\s/@]+(\/|$|\?)/.test(url)) throw new AntispamError('checkout_insecure');
  return { donationId: id(c.donation_id), checkoutUrl: url, provider: str(c.provider, 64) };
}

const STATUSES: DonationStatus[] = ['pending', 'paid', 'failed', 'expired'];

export function parseDonations(json: unknown): Donation[] {
  const root = obj(json);
  if (!Array.isArray(root.donations)) throw new ConexusAccountError('invalid_response');
  return root.donations.slice(0, 100).map((item) => {
    const d = obj(item);
    const status = str(d.status, 32) as DonationStatus;
    return {
      id: id(d.id),
      amountCents: int(d.amount_cents),
      currency: str(d.currency, 8),
      status: STATUSES.includes(status) ? status : 'pending',
      productSlug: optStr(d.product_slug, 120),
      createdAt: str(d.created_at, 64),
    };
  });
}

// ---------- Clientes ----------

type RequestOpts = Parameters<ConexusSession['authorizedRequest']>[1];

const API = '/api/v1/antispam';

export function createAntispamApi(session: ConexusSession) {
  /**
   * Caminho relativo → `apiBaseUrl` da conta. A API do AntiSpam responde 403 `forbidden` quando o token não foi
   * emitido para este app: o pacote encerra a sessão local (`disconnectOnForbidden`) e aqui vira `reauth_required`.
   */
  async function call(path: string, opts: RequestOpts) {
    try {
      return await session.authorizedRequest(`${API}${path}`, opts, { disconnectOnForbidden: true });
    } catch (e) {
      if (isConexusAccountError(e, 'forbidden')) throw new AntispamError('reauth_required');
      throw e;
    }
  }
  return {
    /** null quando não há backup na nuvem (404). */
    async getBackup(): Promise<CloudBackup | null> {
      try {
        const res = await call('/backup', { method: 'GET' });
        return parseBackup(res.json);
      } catch (e) {
        if (isConexusAccountError(e, 'not_found')) return null;
        throw e;
      }
    },
    async putBackup(payload: CloudBackupPayload): Promise<{ updatedAt: string }> {
      const res = await call('/backup', { method: 'PUT', json: payload });
      const body = obj(res.json);
      return { updatedAt: str(body.updated_at, 64) };
    },
    async deleteBackup(): Promise<void> {
      try {
        await call('/backup', { method: 'DELETE' });
      } catch (e) {
        if (!isConexusAccountError(e, 'not_found')) throw e;
      }
    },
    async createReport(input: { number: string; category: ReportCategory; note?: string; occurredAt?: string }): Promise<CloudReport> {
      const number = toE164(input.number);
      if (!number) throw new AntispamError('number_not_supported');
      const note = input.note?.trim();
      const res = await call('/reports', {
        method: 'POST',
        json: {
          number,
          category: toCloudCategory(input.category),
          ...(note ? { note: note.slice(0, 500) } : {}),
          ...(input.occurredAt ? { occurred_at: input.occurredAt } : {}),
        },
      });
      return parseReport(res.json);
    },
    async listReports(page = 1): Promise<CloudReportPage> {
      const res = await call(`/reports?page=${Math.max(1, Math.floor(page))}`, { method: 'GET' });
      return parseReportPage(res.json);
    },
    async deleteReport(reportId: string): Promise<void> {
      await call(`/reports/${encodeURIComponent(reportId)}`, { method: 'DELETE' });
    },
    /** Apaga backup e denúncias do usuário no servidor. */
    async deleteMyData(): Promise<void> {
      await call('/me', { method: 'DELETE' });
    },
  };
}

/** `paymentsUrl`: URL base HTTPS do Payments BFF (fora da API do app, por isso absoluta). */
export function createPaymentsApi(session: ConexusSession, paymentsUrl: string) {
  const url = `${paymentsUrl}/v1/me/donations`;
  return {
    async donate(amountCents: number, message?: string | null): Promise<DonationCheckout> {
      const body = donationBody(amountCents, message);
      const res = await session.authorizedRequest(url, { method: 'POST', json: body });
      return parseCheckout(res.json);
    },
    async listDonations(): Promise<Donation[]> {
      const res = await session.authorizedRequest(url, { method: 'GET' });
      return parseDonations(res.json);
    },
  };
}
