/**
 * Tokens W10M: contraste AA (4,5:1) do texto secundário, do destaque como texto e das
 * cores semânticas nos dois temas (equivalente ao ThemeTest do Conexus Launcher).
 */
import {
  BRAND_PRIMARY,
  ESSENTIALS,
  contrast,
  darkPalette,
  forBackground,
  forWhiteText,
  lightPalette,
  scoreColor,
} from '../src/ui/theme';

describe('W10M theme tokens', () => {
  it('usa as paletas do Windows 10 Mobile', () => {
    expect(darkPalette).toMatchObject({ bg: '#000000', chrome: '#1A1A1A', text: '#FFFFFF', textDim: '#CCCCCC', border: '#333333' });
    expect(lightPalette).toMatchObject({ bg: '#FFFFFF', chrome: '#F2F2F2', text: '#000000', textDim: '#505050', border: '#CCCCCC' });
    expect(darkPalette.accent).toBe(BRAND_PRIMARY);
  });

  it.each([darkPalette, lightPalette])('texto e cores semânticas passam 4,5:1 (dark=%#)', (p) => {
    for (const c of [p.text, p.textDim, p.primary, p.danger, p.warn, p.info, p.mute]) {
      expect(contrast(c, p.bg)).toBeGreaterThanOrEqual(4.5);
    }
    for (const s of [0, 30, 50, 70, 90]) expect(contrast(scoreColor(s, p), p.bg)).toBeGreaterThanOrEqual(4.5);
  });

  it('ajusta todas as essenciais para texto sobre preto e branco e para texto branco', () => {
    for (const c of Object.values(ESSENTIALS)) {
      expect(contrast(forBackground(c, '#000000'), '#000000')).toBeGreaterThanOrEqual(4.5);
      expect(contrast(forBackground(c, '#FFFFFF'), '#FFFFFF')).toBeGreaterThanOrEqual(4.5);
      expect(contrast(forWhiteText(c), '#FFFFFF')).toBeGreaterThanOrEqual(4.5);
    }
  });

  it('mantém cores que já passam', () => {
    expect(forBackground('#60A917', '#000000')).toBe('#60A917');
  });
});
