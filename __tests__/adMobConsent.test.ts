/**
 * AdMob: consentimento UMP antes de qualquer requisição e nenhuma requisição sem unidade configurada.
 */
type ConsentInfo = {
  status: string;
  canRequestAds: boolean;
  privacyOptionsRequirementStatus: string;
};

const ENV_KEYS = [
  'EXPO_PUBLIC_ADMOB_USE_TEST_IDS',
  'EXPO_PUBLIC_ADMOB_BANNER_ID',
  'EXPO_PUBLIC_ADMOB_INTERSTITIAL_ID',
  'EXPO_PUBLIC_ADMOB_TEST_DEVICE_IDS',
] as const;

function setEnv(values: Partial<Record<(typeof ENV_KEYS)[number], string>>) {
  for (const k of ENV_KEYS) delete process.env[k];
  Object.assign(process.env, values);
}

function loadService(gatherConsent: jest.Mock, getConsentInfo = jest.fn().mockRejectedValue(new Error('offline'))) {
  const initialize = jest.fn().mockResolvedValue(undefined);
  const createForAdRequest = jest.fn();
  jest.resetModules();
  jest.doMock('expo-constants', () => ({
    __esModule: true,
    default: { appOwnership: null, expoConfig: { version: '0.4.0' } },
  }));
  jest.doMock(
    'react-native-google-mobile-ads',
    () => ({
      __esModule: true,
      default: () => ({ initialize, setRequestConfiguration: jest.fn() }),
      AdsConsent: { gatherConsent, getConsentInfo },
      AdsConsentStatus: { OBTAINED: 'OBTAINED', NOT_REQUIRED: 'NOT_REQUIRED', REQUIRED: 'REQUIRED' },
      AdsConsentPrivacyOptionsRequirementStatus: { REQUIRED: 'REQUIRED', NOT_REQUIRED: 'NOT_REQUIRED' },
      InterstitialAd: { createForAdRequest },
      AdEventType: { CLOSED: 'closed' },
    }),
    { virtual: true },
  );
  const mod: typeof import('../src/ads/adMob') = require('../src/ads/adMob');
  return { service: mod.adMobService, mod, initialize, createForAdRequest };
}

function info(overrides: Partial<ConsentInfo>): ConsentInfo {
  return { status: 'NOT_REQUIRED', canRequestAds: true, privacyOptionsRequirementStatus: 'NOT_REQUIRED', ...overrides };
}

afterAll(() => setEnv({}));

describe('adMob sem unidades (production sem env)', () => {
  beforeEach(() => setEnv({ EXPO_PUBLIC_ADMOB_USE_TEST_IDS: 'false' }));

  it('não pede consentimento, não inicializa o SDK e não renderiza banner', async () => {
    const gather = jest.fn().mockResolvedValue(info({}));
    const { service, initialize } = loadService(gather);
    expect(service.isEnabled()).toBe(false);
    expect(service.getBannerUnitId()).toBe('');
    await expect(service.initialize()).resolves.toBe(false);
    await expect(service.recordNaturalBreak()).resolves.toBe(false);
    expect(gather).not.toHaveBeenCalled();
    expect(initialize).not.toHaveBeenCalled();
  });

  it('nunca cai nas unidades de teste do Google quando USE_TEST_IDS não é "true"', () => {
    setEnv({});
    const { mod } = loadService(jest.fn());
    expect(mod.bannerUnitId()).toBe('');
    expect(mod.interstitialUnitId()).toBe('');
  });
});

describe('adMob com unidades de teste (development/preview)', () => {
  beforeEach(() => setEnv({ EXPO_PUBLIC_ADMOB_USE_TEST_IDS: 'true' }));

  it('usa as unidades de exemplo do Google', () => {
    const { mod } = loadService(jest.fn());
    expect(mod.bannerUnitId()).toBe(mod.GOOGLE_TEST.banner);
    expect(mod.interstitialUnitId()).toBe(mod.GOOGLE_TEST.interstitial);
  });

  it('pede anúncio personalizado quando consentimento não é exigido', async () => {
    const { service, initialize } = loadService(jest.fn().mockResolvedValue(info({})));
    await expect(service.initialize()).resolves.toBe(true);
    expect(initialize).toHaveBeenCalledTimes(1);
    expect(service.getRequestOptions()).toEqual({ requestNonPersonalizedAdsOnly: false });
  });

  it('consentimento é colhido antes do init do SDK', async () => {
    const order: string[] = [];
    const gather = jest.fn(async () => {
      order.push('consent');
      return info({});
    });
    const { service, initialize } = loadService(gather);
    initialize.mockImplementation(async () => {
      order.push('init');
    });
    await service.initialize();
    expect(order).toEqual(['consent', 'init']);
  });

  it('não inicializa o SDK quando o UMP não permite anúncios', async () => {
    const { service, initialize } = loadService(
      jest.fn().mockResolvedValue(info({ status: 'REQUIRED', canRequestAds: false })),
    );
    await expect(service.initialize()).resolves.toBe(false);
    expect(initialize).not.toHaveBeenCalled();
  });

  it('sem resposta de consentimento: nenhuma requisição agora e nova tentativa depois', async () => {
    const gather = jest.fn().mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(info({}));
    const { service, initialize } = loadService(gather);
    await expect(service.initialize()).resolves.toBe(false);
    expect(initialize).not.toHaveBeenCalled();
    await expect(service.initialize()).resolves.toBe(true);
    expect(gather).toHaveBeenCalledTimes(2);
  });

  it('usa o consentimento salvo quando o formulário falha', async () => {
    const { service, initialize } = loadService(
      jest.fn().mockRejectedValue(new Error('form')),
      jest.fn().mockResolvedValue(info({ status: 'REQUIRED', canRequestAds: true })),
    );
    await expect(service.initialize()).resolves.toBe(true);
    expect(initialize).toHaveBeenCalledTimes(1);
    expect(service.getRequestOptions()).toEqual({ requestNonPersonalizedAdsOnly: true });
  });

  it('expõe as opções de privacidade quando o UMP exige', async () => {
    const { service } = loadService(
      jest.fn().mockResolvedValue(info({ status: 'OBTAINED', privacyOptionsRequirementStatus: 'REQUIRED' })),
    );
    await service.initialize();
    expect(service.isPrivacyOptionsRequired()).toBe(true);
  });

  it('colhe consentimento uma vez só com chamadas concorrentes', async () => {
    const gather = jest.fn().mockResolvedValue(info({}));
    const { service } = loadService(gather);
    await Promise.all([service.initialize(), service.whenReady()]);
    expect(gather).toHaveBeenCalledTimes(1);
  });

  it('intersticial não aparece na 1ª verificação da sessão', async () => {
    const { service, createForAdRequest } = loadService(jest.fn().mockResolvedValue(info({})));
    createForAdRequest.mockReturnValue({ load: jest.fn(), show: jest.fn(), loaded: true, addAdEventListener: jest.fn(() => jest.fn()) });
    await expect(service.recordNaturalBreak()).resolves.toBe(false);
  });
});
