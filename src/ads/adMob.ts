/**
 * AdMob do AntiSpam BR (padrão do public-mock-exams, só banner + intersticial).
 *
 * Regras deste app:
 * - Anúncio só DENTRO do app, em telas de lista/histórico/ajustes e numa pausa natural da aba Verificar.
 *   Nada no fluxo de chamada: o CallScreeningService nativo, notificações e overlays não conhecem este módulo.
 * - Consentimento UMP antes de qualquer requisição; sem consentimento possível, nenhuma requisição.
 * - Sem unidade configurada (production sem env), o SDK nem é inicializado.
 * - Nada aqui é aguardado pelo boot nem pela triagem: tudo é preguiçoso e sob demanda da UI.
 */
import { Platform } from 'react-native';
import Constants from 'expo-constants';

import { ADMOB } from './config';
import {
  ACTIONS_BEFORE_INTERSTITIAL,
  canShowInterstitial,
  initialGate,
  markInterstitialShown,
} from './frequency';

type AdsModule = typeof import('react-native-google-mobile-ads');

type FullscreenLike = {
  load: () => void;
  show: () => Promise<void> | void;
  loaded?: boolean;
  addAdEventListener: (type: unknown, cb: (...args: unknown[]) => void) => () => void;
};

type ConsentInfoLike = {
  status: string;
  canRequestAds: boolean;
  privacyOptionsRequirementStatus: string;
};

/** Unidades de exemplo do Google: só com EXPO_PUBLIC_ADMOB_USE_TEST_IDS=true. */
export const GOOGLE_TEST = {
  banner: Platform.select({
    ios: 'ca-app-pub-3940256099942544/2934735716',
    default: 'ca-app-pub-3940256099942544/6300978111',
  })!,
  interstitial: Platform.select({
    ios: 'ca-app-pub-3940256099942544/4411468910',
    default: 'ca-app-pub-3940256099942544/1033173712',
  })!,
};

function pickUnit(testId: string, publisherId: string): string {
  if (ADMOB.useTestIds) return testId;
  return publisherId;
}

export function bannerUnitId(): string {
  return pickUnit(GOOGLE_TEST.banner, ADMOB.bannerUnitId);
}

export function interstitialUnitId(): string {
  return pickUnit(GOOGLE_TEST.interstitial, ADMOB.interstitialUnitId);
}

function hasAnyUnit(): boolean {
  return bannerUnitId() !== '' || interstitialUnitId() !== '';
}

let adsModule: AdsModule | null = null;
let adsAvailable: boolean | null = null;
let interstitial: FullscreenLike | null = null;
let personalizedAds = false;
let privacyOptionsRequired = false;
let initPromise: Promise<boolean> | null = null;
let actionsSinceAd = 0;
const gate = initialGate();

function isExpoGo(): boolean {
  return Constants.appOwnership === 'expo';
}

async function loadAdsModule(): Promise<AdsModule | null> {
  if (isExpoGo()) {
    adsAvailable = false;
    return null;
  }
  if (adsModule) return adsModule;
  try {
    // require preguiçoso (e não import()): o Jest do repo não roda import() dinâmico, e no app o
    // custo do módulo nativo só é pago quando a primeira tela com anúncio aparece.
    adsModule = require('react-native-google-mobile-ads') as AdsModule;
    return adsModule;
  } catch {
    adsAvailable = false;
    return null;
  }
}

function requestOpts() {
  return { requestNonPersonalizedAdsOnly: !personalizedAds };
}

function applyConsentInfo(ads: AdsModule, info: ConsentInfoLike) {
  personalizedAds =
    info.status === ads.AdsConsentStatus.OBTAINED || info.status === ads.AdsConsentStatus.NOT_REQUIRED;
  privacyOptionsRequired =
    info.privacyOptionsRequirementStatus === ads.AdsConsentPrivacyOptionsRequirementStatus.REQUIRED;
}

/**
 * UMP antes da 1ª requisição. Se o formulário falhar, usa o consentimento já salvo no aparelho;
 * se nem isso existir, NÃO pede anúncio (e tenta de novo numa próxima tela).
 */
async function gatherConsent(ads: AdsModule): Promise<boolean | null> {
  const options = ADMOB.testDeviceIds.length > 0 ? { testDeviceIdentifiers: ADMOB.testDeviceIds } : undefined;
  try {
    const info = (await ads.AdsConsent.gatherConsent(options)) as ConsentInfoLike;
    applyConsentInfo(ads, info);
    return info.canRequestAds;
  } catch {
    try {
      const info = (await ads.AdsConsent.getConsentInfo()) as ConsentInfoLike;
      applyConsentInfo(ads, info);
      return info.canRequestAds;
    } catch {
      personalizedAds = false;
      return null;
    }
  }
}

function safeLoad(ad: FullscreenLike | null) {
  if (!ad || ad.loaded) return;
  try {
    ad.load();
  } catch {
    // módulo nativo ausente
  }
}

function createInterstitial(ads: AdsModule): FullscreenLike | null {
  const unit = interstitialUnitId();
  if (!unit) return null;
  return ads.InterstitialAd.createForAdRequest(unit, requestOpts()) as unknown as FullscreenLike;
}

function showLoaded(ad: FullscreenLike, ads: AdsModule): Promise<boolean> {
  if (!ad.loaded) return Promise.resolve(false);
  gate.showing = true;
  return new Promise((resolve) => {
    let settled = false;
    const unsubscribe = ad.addAdEventListener(ads.AdEventType.CLOSED, () => finish(true));
    const finish = (shown: boolean) => {
      if (settled) return;
      settled = true;
      gate.showing = false;
      unsubscribe();
      safeLoad(ad);
      resolve(shown);
    };
    try {
      void Promise.resolve(ad.show()).catch(() => finish(false));
    } catch {
      finish(false);
    }
  });
}

export const adMobService = {
  /** Há algo a exibir neste build (unidade configurada e não é Expo Go)? */
  isEnabled(): boolean {
    if (adsAvailable === false) return false;
    return !isExpoGo() && hasAnyUnit();
  },

  /** Módulo do SDK já carregado (após `whenReady()` resolver `true`). */
  getAdsModule(): AdsModule | null {
    return adsModule;
  },

  getBannerUnitId(): string {
    return bannerUnitId();
  },

  getRequestOptions() {
    return requestOpts();
  },

  isPrivacyOptionsRequired(): boolean {
    return privacyOptionsRequired;
  },

  async showPrivacyOptions(): Promise<void> {
    const ads = await loadAdsModule();
    if (!ads) return;
    const info = (await ads.AdsConsent.showPrivacyOptionsForm()) as ConsentInfoLike;
    applyConsentInfo(ads, info);
  },

  /** Consentimento + init do SDK, uma vez. `false` = não pedir anúncio. */
  initialize(): Promise<boolean> {
    if (!hasAnyUnit()) return Promise.resolve(false);
    initPromise ??= this.initializeOnce().then((ok) => {
      if (ok === null) {
        initPromise = null; // sem resposta de consentimento: tenta de novo depois, sem anúncio agora
        return false;
      }
      return ok;
    });
    return initPromise;
  },

  async initializeOnce(): Promise<boolean | null> {
    const ads = await loadAdsModule();
    if (!ads) return false;
    try {
      const canRequest = await gatherConsent(ads);
      if (canRequest !== true) return canRequest;
      if (ADMOB.testDeviceIds.length > 0) {
        await ads.default().setRequestConfiguration({ testDeviceIdentifiers: ADMOB.testDeviceIds });
      }
      await ads.default().initialize();
      adsAvailable = true;
      return true;
    } catch (err) {
      console.warn('AdMob init falhou:', err);
      adsAvailable = false;
      return false;
    }
  },

  whenReady(): Promise<boolean> {
    return this.initialize();
  },

  async preloadInterstitial(): Promise<void> {
    if (!interstitialUnitId() || !(await this.initialize()) || !adsModule) return;
    interstitial ??= createInterstitial(adsModule);
    safeLoad(interstitial);
  },

  /**
   * Pausa natural: fim de uma verificação manual. Mostra 1 a cada N, com cooldown e teto por hora.
   * Nunca chamar do fluxo de chamada nem de notificação.
   */
  async recordNaturalBreak(): Promise<boolean> {
    if (!interstitialUnitId()) return false;
    actionsSinceAd += 1;
    if (actionsSinceAd < ACTIONS_BEFORE_INTERSTITIAL) {
      if (actionsSinceAd === ACTIONS_BEFORE_INTERSTITIAL - 1) void this.preloadInterstitial();
      return false;
    }
    if (!(await this.initialize()) || !adsModule) return false;
    if (!canShowInterstitial(gate)) {
      void this.preloadInterstitial();
      return false;
    }
    interstitial ??= createInterstitial(adsModule);
    if (!interstitial?.loaded) {
      safeLoad(interstitial);
      return false;
    }
    const shown = await showLoaded(interstitial, adsModule);
    if (shown) {
      markInterstitialShown(gate);
      actionsSinceAd = 0;
    }
    return shown;
  },
};
