/**
 * Backup na nuvem (opcional e manual): serializa as listas, regras e ajustes que o app guarda no
 * AsyncStorage (`src/app/store.ts`) no formato `data` do contrato `PUT /api/v1/antispam/backup`.
 * Ficam DE FORA: histórico de chamadas, base de reputação e a chave da IA (OpenRouter).
 * A restauração reaproveita a validação estrita do backup local (`src/app/backup.ts`).
 */
import { validateBackup } from '../app/backup';
import type { AppState } from '../app/store';
import { MODE_DEFAULTS, type DecisionEngineConfig } from '../core/decision/decisionEngine';
import type { ProtectionMode, Rule } from '../core/types';
import { AntispamError, type CloudBackupPayload } from './api';

export const CLOUD_BACKUP_VERSION = 1 as const;
export const CLOUD_BACKUP_MAX_BYTES = 256 * 1024;

export type BackupSource = Pick<AppState, 'mode' | 'config' | 'rules' | 'allowlist' | 'blacklist' | 'settings'>;

export interface BackupSummary {
  blocked: number;
  allowed: number;
  rules: number;
  mode: ProtectionMode;
}

export interface RestorePatch {
  mode: ProtectionMode;
  config: DecisionEngineConfig;
  rules: Rule[];
  allowlist: string[];
  blacklist: string[];
  settings: AppState['settings'];
}

export function serializeBackup(s: BackupSource, deviceName?: string | null): CloudBackupPayload {
  const settings: Record<string, unknown> = {
    mode: s.mode,
    notifications: s.settings.notifications,
    haptics: s.settings.haptics,
  };
  if (s.mode === 'CUSTOM') {
    settings.custom = {
      blockThreshold: s.config.blockThreshold,
      silenceThreshold: s.config.silenceThreshold,
      warnThreshold: s.config.warnThreshold,
      handle0303: s.config.handle0303,
      blockUnknownInAggressive: s.config.blockUnknownInAggressive,
    };
  }
  const device = deviceName?.trim().slice(0, 80);
  return {
    version: CLOUD_BACKUP_VERSION,
    ...(device ? { device_name: device } : {}),
    data: {
      blocked: [...s.blacklist],
      allowed: [...s.allowlist],
      rules: s.rules.map((r) => ({ ...r })),
      settings,
    },
  };
}

/** Bytes UTF-8 de uma string (sem depender de TextEncoder). */
export function utf8Length(value: string): number {
  let bytes = 0;
  for (const ch of value) {
    const cp = ch.codePointAt(0) ?? 0;
    bytes += cp < 0x80 ? 1 : cp < 0x800 ? 2 : cp < 0x10000 ? 3 : 4;
  }
  return bytes;
}

/** Tamanho em bytes UTF-8 do corpo JSON. */
export function payloadBytes(payload: CloudBackupPayload): number {
  return utf8Length(JSON.stringify(payload));
}

function isThreshold(v: unknown): v is number {
  return typeof v === 'number' && Number.isFinite(v) && v >= 0 && v <= 101;
}

function parseCustom(v: unknown): DecisionEngineConfig | null {
  if (!v || typeof v !== 'object') return null;
  const c = v as Record<string, unknown>;
  if (!isThreshold(c.blockThreshold) || !isThreshold(c.silenceThreshold) || !isThreshold(c.warnThreshold)) return null;
  if (!['allow', 'identify', 'silence', 'block'].includes(c.handle0303 as string)) return null;
  return {
    mode: 'CUSTOM',
    blockThreshold: c.blockThreshold,
    silenceThreshold: c.silenceThreshold,
    warnThreshold: c.warnThreshold,
    handle0303: c.handle0303 as DecisionEngineConfig['handle0303'],
    blockUnknownInAggressive: c.blockUnknownInAggressive === true,
  };
}

/**
 * Valida o `data` vindo da nuvem (JSON opaco para o servidor) e devolve o patch a aplicar.
 * Qualquer desvio do formato → AntispamError('backup_invalid'); nada é aplicado parcialmente.
 */
export function parseCloudBackupData(data: unknown, current: Pick<AppState, 'settings' | 'config'>): RestorePatch {
  if (!data || typeof data !== 'object' || Array.isArray(data)) throw new AntispamError('backup_invalid');
  const d = data as Record<string, unknown>;
  const settings = (d.settings && typeof d.settings === 'object' && !Array.isArray(d.settings) ? d.settings : {}) as Record<string, unknown>;
  const mode = (settings.mode ?? 'BALANCED') as ProtectionMode;
  const check = validateBackup(
    JSON.stringify({
      schemaVersion: 1,
      app: 'antispam-br',
      exportedAt: new Date(0).toISOString(),
      data: { mode, rules: d.rules, allowlist: d.allowed, blacklist: d.blocked },
    }),
  );
  if (!check.ok) throw new AntispamError('backup_invalid');
  let config: DecisionEngineConfig;
  if (mode === 'CUSTOM') {
    const custom = parseCustom(settings.custom);
    config = custom ?? { ...current.config, mode: 'CUSTOM' };
  } else {
    config = MODE_DEFAULTS[mode];
  }
  return {
    mode,
    config,
    rules: check.backup.data.rules,
    allowlist: check.backup.data.allowlist,
    blacklist: check.backup.data.blacklist,
    settings: {
      notifications: typeof settings.notifications === 'boolean' ? settings.notifications : current.settings.notifications,
      haptics: typeof settings.haptics === 'boolean' ? settings.haptics : current.settings.haptics,
    },
  };
}

export function summarize(patch: Pick<RestorePatch, 'mode' | 'rules' | 'allowlist' | 'blacklist'>): BackupSummary {
  return { blocked: patch.blacklist.length, allowed: patch.allowlist.length, rules: patch.rules.length, mode: patch.mode };
}

export function summaryText(s: BackupSummary): string {
  const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`;
  return `${plural(s.blocked, 'número bloqueado', 'números bloqueados')}, ${plural(s.allowed, 'número permitido', 'números permitidos')} e ${plural(s.rules, 'regra', 'regras')}`;
}
