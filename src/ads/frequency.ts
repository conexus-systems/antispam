/**
 * Limite de frequência do intersticial (função pura, testável sem SDK).
 * Só em pausa natural (fim de uma verificação manual), nunca empilhado e nunca na 1ª ação da sessão.
 */
export const INTERSTITIAL_COOLDOWN_MS = 3 * 60_000;
export const INTERSTITIAL_MAX_PER_HOUR = 3;
/** Uma a cada N verificações concluídas. */
export const ACTIONS_BEFORE_INTERSTITIAL = 3;

export type InterstitialGateState = {
  lastShownAt: number;
  hourWindowStart: number;
  hourCount: number;
  showing: boolean;
};

export function initialGate(): InterstitialGateState {
  return { lastShownAt: 0, hourWindowStart: 0, hourCount: 0, showing: false };
}

export function canShowInterstitial(state: InterstitialGateState, now = Date.now()): boolean {
  if (state.showing) return false;
  if (state.lastShownAt > 0 && now - state.lastShownAt < INTERSTITIAL_COOLDOWN_MS) return false;
  const windowExpired = now - state.hourWindowStart > 3_600_000;
  const hourCount = windowExpired ? 0 : state.hourCount;
  return hourCount < INTERSTITIAL_MAX_PER_HOUR;
}

export function markInterstitialShown(state: InterstitialGateState, now = Date.now()): void {
  if (now - state.hourWindowStart > 3_600_000) {
    state.hourWindowStart = now;
    state.hourCount = 0;
  }
  state.lastShownAt = now;
  state.hourCount += 1;
}
