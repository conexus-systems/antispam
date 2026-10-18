import { useEffect, useState } from "react";
import { Animated, Easing, StyleSheet, View, type StyleProp, type ViewStyle } from "react-native";

import { useMetro } from "./theme";

const DOTS = 5;
const DOT = 4;
const CYCLE_MS = 2200;
const STAGGER = 0.08;

type Props = {
  /** 0–100 para barra determinada; omitido = pontos indeterminados. */
  readonly value?: number;
  /** Cor dos pontos/barra (padrão: destaque legível; use a cor do texto dentro de botões). */
  readonly color?: string;
  /** Largura da faixa dos pontos (padrão: a do pai). */
  readonly width?: number;
  readonly accessibilityLabel?: string;
  readonly style?: StyleProp<ViewStyle>;
};

/**
 * Progresso W10M. Indeterminado: cinco pontos atravessam a faixa, mais lentos no meio. Determinado: barra de 4 dp.
 * Com "reduzir movimento" os pontos ficam parados no centro. Anunciado como barra de progresso.
 */
export function MetroProgress({ value, color, width, accessibilityLabel, style }: Props) {
  const { c, accent, reduceMotion } = useMetro();
  const tint = color ?? accent.text;
  const [measured, setMeasured] = useState(width ?? 0);
  const [phase] = useState(() => new Animated.Value(0));
  const determinate = value != null;
  const span = width ?? measured;

  useEffect(() => {
    if (determinate || reduceMotion) return;
    const loop = Animated.loop(
      Animated.timing(phase, { toValue: 1, duration: CYCLE_MS, easing: Easing.linear, useNativeDriver: true }),
    );
    loop.start();
    return () => loop.stop();
  }, [determinate, phase, reduceMotion]);

  const label = accessibilityLabel ?? (determinate ? `${Math.round(value)}%` : "Carregando");

  if (determinate) {
    const pct = Math.max(0, Math.min(100, value));
    return (
      <View
        accessibilityRole="progressbar"
        accessibilityLabel={label}
        accessibilityValue={{ min: 0, max: 100, now: pct }}
        style={[styles.track, { backgroundColor: c.surface }, style]}
      >
        <View style={{ width: `${pct}%`, height: "100%", backgroundColor: accent.fill }} />
      </View>
    );
  }

  return (
    <View
      accessibilityRole="progressbar"
      accessibilityLabel={label}
      accessibilityLiveRegion="polite"
      onLayout={width == null ? (event) => setMeasured(event.nativeEvent.layout.width) : undefined}
      style={[styles.dots, width != null && { width }, style]}
    >
      {span > 0 &&
        Array.from({ length: DOTS }, (_, i) => {
          if (reduceMotion) {
            const x = span / 2 + (i - (DOTS - 1) / 2) * DOT * 2.5 - DOT / 2;
            return <View key={i} style={[styles.dot, { backgroundColor: tint, transform: [{ translateX: x }] }]} />;
          }
          const start = i * STAGGER;
          const translateX = phase.interpolate({
            inputRange: [start, start + 0.3, start + 0.55, start + 0.85],
            outputRange: [-DOT, span * 0.45, span * 0.55, span + DOT],
            extrapolate: "clamp",
          });
          const opacity = phase.interpolate({
            inputRange: [start, start + 0.02, start + 0.83, start + 0.85],
            outputRange: [0, 1, 1, 0],
            extrapolate: "clamp",
          });
          return <Animated.View key={i} style={[styles.dot, { backgroundColor: tint, opacity, transform: [{ translateX }] }]} />;
        })}
    </View>
  );
}

const styles = StyleSheet.create({
  track: { height: 4, alignSelf: "stretch", overflow: "hidden" },
  dots: { height: 8, alignSelf: "stretch", overflow: "hidden", justifyContent: "center" },
  dot: { position: "absolute", left: 0, width: DOT, height: DOT },
});
