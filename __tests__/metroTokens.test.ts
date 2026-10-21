/**
 * Tokens W10M do AntiSpam BR: cor de destaque padrão (brand.primary), validação de hex,
 * sanitização das preferências lidas do armazenamento e contraste AA do destaque.
 */
import { BRAND_PRIMARY, ACCENT_CHOICES, DEFAULT_APPEARANCE, normalizeHex, resolveAccent, sanitizeAppearance } from '../src/ui/metro/tokens';
import { FLUENT_PATHS } from '../src/ui/metro/fluentPaths';
import { contrast, paletteFor } from '../src/ui/theme';

describe('tokens W10M', () => {
  it('usa brand.primary (fleet.yml) como destaque padrão', () => {
    expect(BRAND_PRIMARY).toBe('#DC2626');
    expect(DEFAULT_APPEARANCE).toEqual({ theme: 'system', accent: '#DC2626', reduceMotion: false });
    expect(ACCENT_CHOICES[0].hex).toBe(BRAND_PRIMARY);
  });

  it.each([
    ['1ba1e2', '#1BA1E2'],
    ['#F80', '#FF8800'],
    ['  #dc2626 ', '#DC2626'],
  ])('normaliza %s', (input, expected) => {
    expect(normalizeHex(input)).toBe(expected);
  });

  it.each(['', 'red', '#12345', '#GGGGGG', '#1234567', 'rgb(1,2,3)'])('rejeita %s', (input) => {
    expect(normalizeHex(input)).toBeNull();
  });

  it('resolveAccent cai no padrão para valores inválidos', () => {
    expect(resolveAccent('#00ff00')).toBe('#00FF00');
    expect(resolveAccent('nope')).toBe(BRAND_PRIMARY);
    expect(resolveAccent(undefined)).toBe(BRAND_PRIMARY);
    expect(resolveAccent(42)).toBe(BRAND_PRIMARY);
  });

  it('sanitizeAppearance valida tema, destaque e movimento', () => {
    expect(sanitizeAppearance(null)).toEqual(DEFAULT_APPEARANCE);
    expect(sanitizeAppearance({ theme: 'neon' as never, accent: 'xyz', reduceMotion: 'yes' as never })).toEqual(DEFAULT_APPEARANCE);
    expect(sanitizeAppearance({ theme: 'light', accent: '#1ba1e2', reduceMotion: true })).toEqual({
      theme: 'light',
      accent: '#1BA1E2',
      reduceMotion: true,
    });
  });

  it.each(ACCENT_CHOICES.map((c) => [c.label, c.hex] as const))('destaque %s passa 4,5:1 como texto nos dois temas', (_label, hex) => {
    for (const dark of [true, false]) {
      const p = paletteFor(dark, hex);
      expect(contrast(p.primary, p.bg)).toBeGreaterThanOrEqual(4.5);
      expect(contrast(p.accentFill, '#FFFFFF')).toBeGreaterThanOrEqual(4.5);
    }
  });

  it('paletteFor usa o destaque pedido', () => {
    expect(paletteFor(true, '#1BA1E2').accent).toBe('#1BA1E2');
    expect(paletteFor(true).accent).toBe(BRAND_PRIMARY);
  });
});

describe('caminhos Fluent', () => {
  it('traz os 13 ícones usados pelo app, todos com path', () => {
    const names = ['add', 'alert', 'block', 'check', 'copy', 'delete', 'flag', 'heart', 'history', 'lock', 'search', 'settings', 'shield'];
    for (const n of names) expect(FLUENT_PATHS).toHaveProperty(n);
    for (const d of Object.values(FLUENT_PATHS)) expect(d.length).toBeGreaterThan(10);
  });
});
