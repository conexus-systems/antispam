import {
  INTERSTITIAL_COOLDOWN_MS,
  INTERSTITIAL_MAX_PER_HOUR,
  canShowInterstitial,
  initialGate,
  markInterstitialShown,
} from '../src/ads/frequency';

describe('limite de frequência do intersticial', () => {
  it('libera o primeiro', () => {
    expect(canShowInterstitial(initialGate(), 1_000_000)).toBe(true);
  });

  it('respeita o cooldown', () => {
    const g = initialGate();
    markInterstitialShown(g, 1_000_000);
    expect(canShowInterstitial(g, 1_000_000 + INTERSTITIAL_COOLDOWN_MS - 1)).toBe(false);
    expect(canShowInterstitial(g, 1_000_000 + INTERSTITIAL_COOLDOWN_MS)).toBe(true);
  });

  it('respeita o teto por hora e reabre após a janela', () => {
    const g = initialGate();
    let t = 10_000_000;
    for (let i = 0; i < INTERSTITIAL_MAX_PER_HOUR; i++) {
      expect(canShowInterstitial(g, t)).toBe(true);
      markInterstitialShown(g, t);
      t += INTERSTITIAL_COOLDOWN_MS;
    }
    expect(canShowInterstitial(g, t)).toBe(false);
    expect(canShowInterstitial(g, g.hourWindowStart + 3_600_001)).toBe(true);
  });

  it('nunca empilha com outro em exibição', () => {
    const g = initialGate();
    g.showing = true;
    expect(canShowInterstitial(g, 1)).toBe(false);
  });
});
