import { forwardRef, useEffect, useRef, useState, type ComponentRef, type ReactNode } from "react";
import {
  Animated,
  Easing,
  Pressable,
  type GestureResponderEvent,
  type LayoutChangeEvent,
  type PressableProps,
  type PressableStateCallbackType,
  type StyleProp,
  type ViewStyle,
} from "react-native";

import { useMetro } from "./theme";
import { motion, staggerDelay, tiltAngles } from "./tokens";

/** Curva desacelerada W10M (0.1, 0.9, 0.2, 1). */
export const metroEasing = Easing.bezier(...motion.easing);

type EnterProps = {
  readonly children: ReactNode;
  /** Atraso em ms. */
  readonly delay?: number;
  /** Posição da linha numa lista: atraso escalonado de 20 ms (até 8 linhas). Soma-se a `delay`. */
  readonly index?: number;
  /** Muda o valor para repetir a entrada (ex.: troca de seção). */
  readonly replayKey?: unknown;
  readonly style?: StyleProp<ViewStyle>;
};

function useEntrance(delay: number, replayKey: unknown, duration: number = motion.pageMs) {
  const { reduceMotion } = useMetro();
  const [progress] = useState(() => new Animated.Value(reduceMotion ? 1 : 0));
  useEffect(() => {
    if (reduceMotion) {
      progress.setValue(1);
      return;
    }
    progress.setValue(0);
    const animation = Animated.timing(progress, { toValue: 1, duration, delay, easing: metroEasing, useNativeDriver: true });
    animation.start();
    return () => animation.stop();
  }, [delay, duration, progress, reduceMotion, replayKey]);
  return progress;
}

/** Entrada de página W10M: sobe 20 dp e aparece em 250 ms, desacelerando. Sem animação com "reduzir movimento". */
export function MetroEnter({ children, delay = 0, index, replayKey, style }: EnterProps) {
  const progress = useEntrance(delay + (index != null ? staggerDelay(index) : 0), replayKey);
  const translateY = progress.interpolate({ inputRange: [0, 1], outputRange: [motion.travel, 0] });
  return <Animated.View style={[style, { opacity: progress, transform: [{ translateY }] }]}>{children}</Animated.View>;
}

type SlideProps = {
  readonly children: ReactNode;
  /** +1 = veio da próxima seção (entra pela direita), -1 = da anterior, 0 = sem deslize. */
  readonly direction: number;
  /** Muda a cada troca (ex.: a chave da seção). */
  readonly replayKey: unknown;
  readonly style?: StyleProp<ViewStyle>;
};

/** Troca de seção do pivô: o conteúdo novo entra deslizando do lado de onde veio (Motion.slideIn). */
export function MetroSlideIn({ children, direction, replayKey, style }: SlideProps) {
  const progress = useEntrance(0, replayKey, motion.fastMs + 60);
  const offset = direction === 0 ? 0 : motion.travel * 2 * Math.sign(direction);
  const translateX = progress.interpolate({ inputRange: [0, 1], outputRange: [offset, 0] });
  return (
    <Animated.View style={[{ flex: 1 }, style, { opacity: direction === 0 ? 1 : progress, transform: [{ translateX }] }]}>
      {children}
    </Animated.View>
  );
}

/**
 * Turnstile W10M: a página entra girando no eixo vertical a partir da borda esquerda (-80° → 0°), como as
 * transições de página do Windows Phone. Escalonável por `index` (efeito "folhas" em listas).
 */
export function MetroTurnstile({ children, delay = 0, index, replayKey, style }: EnterProps) {
  const progress = useEntrance(delay + (index != null ? staggerDelay(index) : 0), replayKey, motion.pageMs + 50);
  const rotateY = progress.interpolate({ inputRange: [0, 1], outputRange: ["-80deg", "0deg"] });
  const translateX = progress.interpolate({ inputRange: [0, 1], outputRange: [-motion.travel * 2, 0] });
  return (
    <Animated.View
      style={[style, { opacity: progress, transform: [{ perspective: 1000 }, { translateX }, { rotateY }] }]}
    >
      {children}
    </Animated.View>
  );
}

/** Empurrão do toque (escala 0,98), desligado com "reduzir movimento". */
export function pressTransform(pressed: boolean, reduceMotion: boolean): ViewStyle | null {
  return pressed && !reduceMotion ? { transform: [{ scale: motion.pressScale }] } : null;
}

/** Estilo de linha pressionada para `Pressable.style`: fundo de toque + empurrão. */
export function usePressedRow() {
  const { c, reduceMotion } = useMetro();
  return ({ pressed }: PressableStateCallbackType): StyleProp<ViewStyle> =>
    pressed ? [{ backgroundColor: c.pressed }, pressTransform(true, reduceMotion)] : null;
}

/** Empurrão animado (100 ms) para espalhar num Pressable: `{ style, onPressIn, onPressOut }`. */
export function usePressScale() {
  const { reduceMotion } = useMetro();
  const scale = useRef(new Animated.Value(1)).current;
  const to = (value: number) => {
    if (reduceMotion) {
      scale.setValue(1);
      return;
    }
    Animated.timing(scale, { toValue: value, duration: motion.quickMs, easing: metroEasing, useNativeDriver: true }).start();
  };
  return {
    style: { transform: [{ scale }] },
    onPressIn: () => to(motion.pressScale),
    onPressOut: () => to(1),
  };
}

export type MetroPressableProps = Omit<PressableProps, "style" | "children"> & {
  readonly style?: StyleProp<ViewStyle> | ((state: { pressed: boolean }) => StyleProp<ViewStyle>);
  readonly children?: ReactNode | ((state: { pressed: boolean }) => ReactNode);
  /** Estilo do invólucro animado (flex, margens). */
  readonly containerStyle?: StyleProp<ViewStyle>;
  /** Inclina na direção do toque (tiles, cartões grandes). Padrão: só o empurrão. */
  readonly tilt?: boolean;
};

/**
 * Pressable com o retorno W10M: empurrão (0,98 em 100 ms) e, com `tilt`, inclinação 3D até 8° para o lado tocado.
 * Sem ripple nem esmaecimento. Desligado com "reduzir movimento". Repassa o ref (funciona em `<Link asChild>`).
 */
export const MetroPressable = forwardRef<ComponentRef<typeof Pressable>, MetroPressableProps>(function MetroPressable(
  { style, children, containerStyle, onPressIn, onPressOut, tilt = false, ...rest },
  ref,
) {
  const { reduceMotion } = useMetro();
  const [values] = useState(() => ({
    scale: new Animated.Value(1),
    rotateX: new Animated.Value(0),
    rotateY: new Animated.Value(0),
  }));
  const size = useRef({ width: 0, height: 0 });

  const animate = (scale: number, rotateX: number, rotateY: number) => {
    if (reduceMotion) return;
    const config = { duration: motion.quickMs, easing: metroEasing, useNativeDriver: true };
    Animated.parallel([
      Animated.timing(values.scale, { toValue: scale, ...config }),
      Animated.timing(values.rotateX, { toValue: rotateX, ...config }),
      Animated.timing(values.rotateY, { toValue: rotateY, ...config }),
    ]).start();
  };

  const transform = tilt
    ? [
        { perspective: 600 },
        { rotateX: values.rotateX.interpolate({ inputRange: [-90, 90], outputRange: ["-90deg", "90deg"] }) },
        { rotateY: values.rotateY.interpolate({ inputRange: [-90, 90], outputRange: ["-90deg", "90deg"] }) },
        { scale: values.scale },
      ]
    : [{ scale: values.scale }];

  return (
    <Animated.View
      style={[containerStyle, { transform }]}
      onLayout={(event: LayoutChangeEvent) => {
        size.current = { width: event.nativeEvent.layout.width, height: event.nativeEvent.layout.height };
      }}
    >
      <Pressable
        ref={ref}
        onPressIn={(event: GestureResponderEvent) => {
          const angles = tilt
            ? tiltAngles(event.nativeEvent.locationX, event.nativeEvent.locationY, size.current.width, size.current.height)
            : { rotateX: 0, rotateY: 0 };
          animate(motion.pressScale, angles.rotateX, angles.rotateY);
          onPressIn?.(event);
        }}
        onPressOut={(event: GestureResponderEvent) => {
          animate(1, 0, 0);
          onPressOut?.(event);
        }}
        style={style}
        {...rest}
      >
        {children}
      </Pressable>
    </Animated.View>
  );
});
