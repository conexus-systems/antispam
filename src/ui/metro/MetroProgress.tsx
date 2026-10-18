/**
 * Progresso W10M: indeterminado = 5 pontos correndo pela largura (lentos no meio);
 * determinado = barra plana de 4 dp. Parado com "reduzir movimento".
 */
import React, { useEffect, useRef, useState } from 'react';
import { Animated, Easing, View, type LayoutChangeEvent } from 'react-native';
import { useTheme } from '../theme';
import { useReduceMotion } from './motion';

/** Posição do ponto no tempo t (0–1): rápido, devagar no meio, rápido (MetroProgress.ease). */
function ease(t: number): number {
  const c = Math.max(0, Math.min(1, t)) - 0.5;
  return Math.max(0, Math.min(1, 0.5 + 4 * c * c * c));
}

export function MetroProgress({ progress = -1, color, dot = 4 }: { progress?: number; color?: string; dot?: number }) {
  const t = useTheme();
  const reduce = useReduceMotion();
  const ink = color ?? t.primary;
  const [width, setWidth] = useState(0);
  const phase = useRef(new Animated.Value(0)).current;
  const indeterminate = progress < 0;

  useEffect(() => {
    if (!indeterminate || reduce || width === 0) return;
    const loop = Animated.loop(Animated.timing(phase, { toValue: 1, duration: 2200, easing: Easing.linear, useNativeDriver: true }));
    loop.start();
    return () => loop.stop();
  }, [indeterminate, reduce, width, phase]);

  const onLayout = (e: LayoutChangeEvent) => setWidth(e.nativeEvent.layout.width);
  const value = Math.max(0, Math.min(100, progress));

  return (
    <View
      onLayout={onLayout}
      accessible
      accessibilityRole="progressbar"
      accessibilityLabel={indeterminate ? 'Carregando' : `${value}%`}
      accessibilityValue={indeterminate ? undefined : { min: 0, max: 100, now: value }}
      style={{ height: 8, justifyContent: 'center', overflow: 'hidden' }}
    >
      {!indeterminate ? (
        <View style={{ height: 4, backgroundColor: t.border }}>
          <View style={{ height: 4, width: `${value}%`, backgroundColor: ink }} />
        </View>
      ) : (
        width > 0 &&
        [0, 1, 2, 3, 4].map((i) => {
          const samples = 21;
          const input = Array.from({ length: samples }, (_, k) => k / (samples - 1));
          const output = input.map((x) => ease((((x - i * 0.07) % 1) + 1) % 1) * width);
          const still = (0.42 + i * 0.04) * width;
          return (
            <Animated.View
              key={i}
              style={{
                position: 'absolute',
                left: 0,
                width: dot,
                height: dot,
                borderRadius: dot / 2,
                backgroundColor: ink,
                transform: [{ translateX: reduce ? still : phase.interpolate({ inputRange: input, outputRange: output }) }],
              }}
            />
          );
        })
      )}
    </View>
  );
}
