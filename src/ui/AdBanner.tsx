import { useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import type { BannerAd as BannerAdType } from 'react-native-google-mobile-ads';

import { adMobService } from '../ads/adMob';
import { spacing, useTheme } from './theme';

/**
 * Banner ancorado para telas de lista/histórico/ajustes. Não renderiza nada sem unidade configurada,
 * no Expo Go, antes do consentimento UMP ou se o SDK faltar. Respiro acima dele para não colar em
 * botões (cliques acidentais violam a política do AdMob).
 */
export function AdBanner() {
  const theme = useTheme();
  const [Banner, setBanner] = useState<typeof BannerAdType | null>(null);
  const [size, setSize] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      if (!adMobService.isEnabled() || !adMobService.getBannerUnitId()) return;
      try {
        if (!(await adMobService.whenReady())) return;
        const ads = adMobService.getAdsModule();
        if (ads && !cancelled) {
          setBanner(() => ads.BannerAd);
          setSize(ads.BannerAdSize.LARGE_ANCHORED_ADAPTIVE_BANNER);
        }
      } catch {
        // Expo Go / módulo nativo ausente
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  if (!Banner || !size) return null;

  return (
    <View collapsable={false} style={[styles.wrap, { backgroundColor: theme.bg, borderTopColor: theme.border }]}>
      <Banner unitId={adMobService.getBannerUnitId()} size={size} requestOptions={adMobService.getRequestOptions()} />
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    alignItems: 'center',
    justifyContent: 'center',
    width: '100%',
    minHeight: 50,
    marginTop: spacing.s,
    borderTopWidth: StyleSheet.hairlineWidth,
  },
});
