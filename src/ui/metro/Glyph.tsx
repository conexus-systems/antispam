/**
 * Catálogo único de ícones (Glyphs): vetores de traço fino (1,7) em grade de 24, mesma família,
 * monocromáticos na cor do texto; o destaque só pinta o estado ativo (quem chama passa a cor).
 */
import React from 'react';
import Svg, { Circle, Path } from 'react-native-svg';

export type GlyphName =
  | 'shield'
  | 'history'
  | 'search'
  | 'settings'
  | 'more'
  | 'heart'
  | 'phone'
  | 'phoneOff'
  | 'lock'
  | 'close'
  | 'check'
  | 'warning'
  | 'add'
  | 'delete'
  | 'flag'
  | 'block'
  | 'copy'
  | 'bell';

const PATHS: Record<GlyphName, string[]> = {
  shield: ['M12 3l7 3v5c0 4.5-3 8.3-7 10-4-1.7-7-5.5-7-10V6l7-3z'],
  history: ['M3.5 12a8.5 8.5 0 1 0 2.5-6', 'M3.5 4v4h4', 'M12 7.5V12l3 2'],
  search: ['M10.5 4a6.5 6.5 0 1 1 0 13 6.5 6.5 0 0 1 0-13z', 'M15.3 15.3L20.5 20.5'],
  settings: [
    'M12 8.8a3.2 3.2 0 1 1 0 6.4 3.2 3.2 0 0 1 0-6.4z',
    'M12 2.8v2.4M12 18.8v2.4M2.8 12h2.4M18.8 12h2.4M5.5 5.5l1.7 1.7M16.8 16.8l1.7 1.7M5.5 18.5l1.7-1.7M16.8 7.2l1.7-1.7',
  ],
  more: [],
  heart: ['M12 20s-7.5-4.6-7.5-10A4.3 4.3 0 0 1 12 7.4 4.3 4.3 0 0 1 19.5 10c0 5.4-7.5 10-7.5 10z'],
  phone: ['M6.5 3.5h3l1.5 4-2 1.3a10 10 0 0 0 6.2 6.2l1.3-2 4 1.5v3a2 2 0 0 1-2 2A16 16 0 0 1 4.5 5.5a2 2 0 0 1 2-2z'],
  phoneOff: [
    'M6.5 3.5h3l1.5 4-2 1.3a10 10 0 0 0 6.2 6.2l1.3-2 4 1.5v3a2 2 0 0 1-2 2A16 16 0 0 1 4.5 5.5a2 2 0 0 1 2-2z',
    'M3 3l18 18',
  ],
  lock: ['M6 10.5h12v10H6z', 'M8.5 10.5V7.5a3.5 3.5 0 0 1 7 0v3'],
  close: ['M5.5 5.5l13 13M18.5 5.5l-13 13'],
  check: ['M4.5 12.5l4.5 4.5 10.5-10.5'],
  warning: ['M12 3.5l9 16H3l9-16z', 'M12 10v4.5', 'M12 16.8v1'],
  add: ['M12 4.5v15M4.5 12h15'],
  delete: ['M4.5 6.5h15', 'M9.5 6.5V4h5v2.5', 'M6.5 6.5l1 13.5h9l1-13.5'],
  flag: ['M5.5 21V4', 'M5.5 4.5h11l-2.5 4 2.5 4h-11'],
  block: ['M12 3.5a8.5 8.5 0 1 1 0 17 8.5 8.5 0 0 1 0-17z', 'M6 6l12 12'],
  copy: ['M8.5 8.5h11v11h-11z', 'M15.5 8.5V4.5h-11v11h4'],
  bell: ['M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 1.5h-15L6 16.5z', 'M10 20.5h4'],
};

export function Glyph({ name, size = 24, color, strokeWidth = 1.7 }: { name: GlyphName; size?: number; color: string; strokeWidth?: number }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" accessible={false}>
      {name === 'more' ? (
        <>
          <Circle cx={5} cy={12} r={1.4} fill={color} />
          <Circle cx={12} cy={12} r={1.4} fill={color} />
          <Circle cx={19} cy={12} r={1.4} fill={color} />
        </>
      ) : (
        PATHS[name].map((d) => (
          <Path key={d} d={d} stroke={color} strokeWidth={strokeWidth} strokeLinecap="square" strokeLinejoin="miter" fill="none" />
        ))
      )}
    </Svg>
  );
}
