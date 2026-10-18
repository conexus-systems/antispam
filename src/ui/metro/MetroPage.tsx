/**
 * Página W10M: fundo do tema, área segura do topo e entrada (sobe 20 dp + aparece, 250 ms).
 * Sem animação com "reduzir movimento". `scroll` envolve o conteúdo num ScrollView vertical.
 */
import React, { useEffect, useRef } from 'react';
import { Animated, Easing, ScrollView, View, type ViewStyle } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { motion, spacing, useTheme } from '../theme';
import { useReduceMotion } from './motion';

export function useEnterAnimation() {
  const reduce = useReduceMotion();
  const progress = useRef(new Animated.Value(0)).current;
  useEffect(() => {
    if (reduce) {
      progress.setValue(1);
      return;
    }
    Animated.timing(progress, {
      toValue: 1,
      duration: motion.pageMs,
      easing: Easing.out(Easing.cubic),
      useNativeDriver: true,
    }).start();
  }, [reduce, progress]);
  return {
    opacity: progress,
    transform: [{ translateY: progress.interpolate({ inputRange: [0, 1], outputRange: [motion.pageRise, 0] }) }],
  };
}

export function MetroPage({
  children,
  header,
  scroll = true,
  contentStyle,
  topInset = true,
}: {
  children: React.ReactNode;
  /** Fica fora do ScrollView (fixo no topo). */
  header?: React.ReactNode;
  scroll?: boolean;
  contentStyle?: ViewStyle;
  topInset?: boolean;
}) {
  const t = useTheme();
  const insets = useSafeAreaInsets();
  const enter = useEnterAnimation();
  const body = scroll ? (
    <ScrollView
      contentContainerStyle={[{ paddingHorizontal: t.gutter, paddingBottom: spacing.xl * 2, gap: spacing.xl }, contentStyle]}
      keyboardShouldPersistTaps="handled"
    >
      {children}
    </ScrollView>
  ) : (
    <View style={[{ flex: 1 }, contentStyle]}>{children}</View>
  );
  return (
    <View style={{ flex: 1, backgroundColor: t.bg, paddingTop: topInset ? insets.top : 0 }}>
      <Animated.View style={[{ flex: 1 }, enter]}>
        {header}
        {body}
      </Animated.View>
    </View>
  );
}
