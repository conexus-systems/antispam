/**
 * Texto com a escala tipográfica W10M (Type.Style). `tone` escolhe a cor do tema; nada de cores soltas.
 */
import React from 'react';
import { Text, type TextProps, type TextStyle } from 'react-native';
import { useTheme, type Theme, type TypeVariant } from '../theme';

export type Tone = 'text' | 'muted' | 'accent' | 'danger' | 'warn' | 'info' | 'mute' | 'disabled' | 'onAccent';

export function toneColor(t: Theme, tone: Tone): string {
  switch (tone) {
    case 'muted': return t.textDim;
    case 'accent': return t.primary;
    case 'danger': return t.danger;
    case 'warn': return t.warn;
    case 'info': return t.info;
    case 'mute': return t.mute;
    case 'disabled': return t.disabled;
    case 'onAccent': return t.onAccent;
    default: return t.text;
  }
}

export function MetroText({
  variant = 'body',
  tone,
  color,
  style,
  ...rest
}: TextProps & { variant?: TypeVariant; tone?: Tone; color?: string; style?: TextStyle | TextStyle[] }) {
  const t = useTheme();
  const defaultTone: Tone = variant === 'caption' ? 'muted' : 'text';
  return <Text {...rest} style={[t.type[variant], { color: color ?? toneColor(t, tone ?? defaultTone) }, style]} />;
}
