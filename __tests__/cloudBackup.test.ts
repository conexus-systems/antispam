import { parseCloudBackupData, payloadBytes, serializeBackup, summarize, summaryText, type BackupSource } from '../src/account/cloudBackup';
import { MODE_DEFAULTS } from '../src/core/decision/decisionEngine';
import { AccountError } from '../src/account/errors';
import type { Rule } from '../src/core/types';

const rule: Rule = { id: 'r1', name: 'Prefixo 0303', enabled: true, action: 'BLOCK', match: { kind: 'prefix', value: '0303' } };

const source: BackupSource = {
  mode: 'CUSTOM',
  config: { ...MODE_DEFAULTS.BALANCED, mode: 'CUSTOM', blockThreshold: 70, handle0303: 'block' },
  rules: [rule],
  allowlist: ['+5511999990000'],
  blacklist: ['+5511988887777', '08001234567'],
  settings: { notifications: false, haptics: true },
};

const current = { settings: { notifications: true, haptics: true }, config: MODE_DEFAULTS.BALANCED };

describe('backup na nuvem', () => {
  it('serializa listas, regras e ajustes no formato do contrato (sem histórico nem chave de IA)', () => {
    const payload = serializeBackup({ ...source, ...({ history: [{ id: 'h' }], ai: { apiKey: 'sk-or-v1-secreta' } } as object) } as BackupSource, 'Android');
    expect(payload.version).toBe(1);
    expect(payload.device_name).toBe('Android');
    expect(payload.data.blocked).toEqual(['+5511988887777', '08001234567']);
    expect(payload.data.allowed).toEqual(['+5511999990000']);
    expect(payload.data.rules).toEqual([rule]);
    expect(payload.data.settings).toMatchObject({ mode: 'CUSTOM', notifications: false, haptics: true, custom: { blockThreshold: 70, handle0303: 'block' } });
    const json = JSON.stringify(payload);
    expect(json).not.toContain('sk-or-v1');
    expect(json).not.toContain('history');
    expect(payloadBytes(payload)).toBeLessThan(256 * 1024);
  });

  it('ida e volta: restaurar devolve o mesmo estado', () => {
    const payload = serializeBackup(source);
    const patch = parseCloudBackupData(JSON.parse(JSON.stringify(payload.data)), current);
    expect(patch).toEqual({
      mode: 'CUSTOM',
      config: source.config,
      rules: source.rules,
      allowlist: source.allowlist,
      blacklist: source.blacklist,
      settings: source.settings,
    });
    expect(summarize(patch)).toEqual({ blocked: 2, allowed: 1, rules: 1, mode: 'CUSTOM' });
    expect(summaryText(summarize(patch))).toBe('2 números bloqueados, 1 número permitido e 1 regra');
  });

  it('modo padrão deriva a config do modo', () => {
    const payload = serializeBackup({ ...source, mode: 'AGGRESSIVE', config: MODE_DEFAULTS.AGGRESSIVE });
    expect(payload.data.settings.custom).toBeUndefined();
    expect(parseCloudBackupData(payload.data, current).config).toEqual(MODE_DEFAULTS.AGGRESSIVE);
  });

  it('rejeita dados malformados sem aplicar nada', () => {
    const bad = [
      null,
      [],
      { blocked: 'x', allowed: [], rules: [] },
      { blocked: [], allowed: [], rules: [{ id: 1 }] },
      { blocked: [], allowed: [], rules: [], settings: { mode: 'TURBO' } },
      { blocked: ['+55'.padEnd(30, '9')], allowed: [], rules: [] },
      { blocked: [], allowed: [], rules: [{ ...rule, match: { kind: 'regex', pattern: '(' } }] },
    ];
    for (const data of bad) {
      expect(() => parseCloudBackupData(data, current)).toThrow(AccountError);
    }
  });
});
