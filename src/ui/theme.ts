/**
 * Design system W10M (Windows 10 Mobile) — tokens do AntiSpam BR.
 *
 * Porte 1:1 dos tokens do Conexus Launcher (ui/Theme.kt, Accent.kt, Type.kt, Motion.kt).
 * Telas e componentes NÃO definem cores, tamanhos de fonte, raios ou durações próprias:
 * usam `useTheme()` (tema do sistema, escuro por padrão) e os componentes de `@conexus/metro-ui` (vendor/conexus/metro-ui).
 *
 * Cor de destaque: a marca era verde (#22C55E = proteção); a essencial W10M mais próxima é
 * Verde #60A917. Vermelho = bloqueio, laranja/âmbar = suspeito (cores do próprio W10M).
 */
import { useColorScheme, type TextStyle } from 'react-native';

// ---------------------------------------------------------------------------
// Cor de destaque (Accent.kt)
// ---------------------------------------------------------------------------

/** As 10 cores essenciais do W10M (Personalização › Cores). */
export const ESSENTIALS = {
  azul: '#0050EF',
  ciano: '#1BA1E2',
  verde: '#60A917',
  lima: '#A4C400',
  laranja: '#FA6800',
  amarelo: '#E3C800',
  vermelho: '#E51400',
  magenta: '#D80073',
  roxo: '#AA00FF',
  cinza: '#647687',
} as const;

/** Extras da paleta W10M usados aqui (faixas de score). */
const AMBAR = '#F0A30A';

/** Destaque do app (verde = proteção). */
export const ACCENT = ESSENTIALS.verde;

type RGB = [number, number, number];

function toRgb(hex: string): RGB {
  const h = hex.replace('#', '');
  return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)];
}

function toHex([r, g, b]: RGB): string {
  return `#${[r, g, b].map((c) => Math.max(0, Math.min(255, Math.round(c))).toString(16).padStart(2, '0')).join('').toUpperCase()}`;
}

export function luminance(hex: string): number {
  const channel = (v: number) => {
    const c = v / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  };
  const [r, g, b] = toRgb(hex);
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
}

/** Razão de contraste WCAG entre duas cores opacas. */
export function contrast(a: string, b: string): number {
  const la = luminance(a);
  const lb = luminance(b);
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
}

function mix(color: string, other: string, amount: number): string {
  const a = toRgb(color);
  const b = toRgb(other);
  return toHex([0, 1, 2].map((i) => Math.trunc(a[i] * (1 - amount) + b[i] * amount)) as RGB);
}

function scale(color: string, factor: number): string {
  return toHex(toRgb(color).map((c) => Math.trunc(c * factor)) as RGB);
}

/**
 * `color` ajustada até passar 4,5:1 como texto/linha sobre `background`: clareada no fundo escuro,
 * escurecida no claro (Accent.forBackground). Cores que já passam voltam iguais.
 */
export function forBackground(color: string, background: string): string {
  let current = color;
  const dark = luminance(background) < 0.5;
  for (let i = 0; i < 40; i++) {
    if (contrast(current, background) >= 4.5) return current;
    current = dark ? mix(current, '#FFFFFF', 0.1) : scale(current, 0.92);
  }
  return current;
}

/** Escurece em passos de 5% até texto branco passar 4,5:1 (Accent.forWhiteText): preenchimentos. */
export function forWhiteText(color: string): string {
  let current = color;
  for (let i = 0; i < 40; i++) {
    if (contrast(current, '#FFFFFF') >= 4.5) return current;
    current = scale(current, 0.95);
  }
  return current;
}

// ---------------------------------------------------------------------------
// Paletas (Theme.kt)
// ---------------------------------------------------------------------------

export type Palette = {
  dark: boolean;
  /** Fundo da página. */
  bg: string;
  /** Superfície elevada: barras, menus, diálogos. */
  chrome: string;
  /** Superfície: botão secundário pressionado, trilhos. */
  surface: string;
  text: string;
  /** Texto secundário (muted). */
  textDim: string;
  /** Divisória / borda discreta. */
  border: string;
  disabled: string;
  /** Destaque cru (preenchimentos com texto branco usam `accentFill`). */
  accent: string;
  /** Destaque ajustado para texto/linha sobre `bg` (>= 4,5:1). */
  primary: string;
  /** Destaque escurecido para texto branco por cima (botão primário, pino ligado). */
  accentFill: string;
  /** Texto sobre `accentFill`. */
  onAccent: string;
  /** Semânticas ajustadas ao fundo (texto/linha). */
  danger: string;
  warn: string;
  info: string;
  mute: string;
  /** Preenchimentos semânticos para texto branco por cima. */
  dangerFill: string;
  warnFill: string;
  /** Fundo do diálogo/menu escurecido. */
  scrim: string;
  /** Campo de texto em foco (sempre branco com texto preto, como no W10M). */
  fieldFocusBg: string;
  fieldFocusText: string;
  fieldFocusPlaceholder: string;
};

function build(dark: boolean): Palette {
  const bg = dark ? '#000000' : '#FFFFFF';
  const on = (c: string) => forBackground(c, bg);
  return {
    dark,
    bg,
    chrome: dark ? '#1A1A1A' : '#F2F2F2',
    surface: dark ? '#2B2B2B' : '#E6E6E6',
    text: dark ? '#FFFFFF' : '#000000',
    textDim: dark ? '#CCCCCC' : '#505050',
    border: dark ? '#333333' : '#CCCCCC',
    disabled: dark ? '#666666' : '#999999',
    accent: ACCENT,
    primary: on(ACCENT),
    accentFill: forWhiteText(ACCENT),
    onAccent: '#FFFFFF',
    danger: on(ESSENTIALS.vermelho),
    warn: on(ESSENTIALS.laranja),
    info: on(ESSENTIALS.ciano),
    mute: on(ESSENTIALS.cinza),
    dangerFill: forWhiteText(ESSENTIALS.vermelho),
    warnFill: forWhiteText(ESSENTIALS.laranja),
    scrim: 'rgba(0,0,0,0.6)',
    fieldFocusBg: '#FFFFFF',
    fieldFocusText: '#000000',
    fieldFocusPlaceholder: '#666666',
  };
}

export const darkPalette = build(true);
export const lightPalette = build(false);

// ---------------------------------------------------------------------------
// Tipografia (Type.kt) — fonte do sistema; Segoe UI não pode ser embarcada.
// Tamanhos em dp escaláveis: o RN aplica a escala de fonte do sistema (allowFontScaling).
// ---------------------------------------------------------------------------

export const type = {
  display: { fontSize: 64, fontWeight: '300', lineHeight: 72 },
  header: { fontSize: 34, fontWeight: '300', lineHeight: 42 },
  title: { fontSize: 28, fontWeight: '300', lineHeight: 34 },
  subtitle: { fontSize: 20, fontWeight: '400', lineHeight: 26 },
  group: { fontSize: 16, fontWeight: '600', lineHeight: 22 },
  body: { fontSize: 15, fontWeight: '400', lineHeight: 21 },
  caption: { fontSize: 12, fontWeight: '400', lineHeight: 16 },
  kicker: { fontSize: 12, fontWeight: '600', lineHeight: 16, letterSpacing: 0.72, textTransform: 'uppercase' },
} as const satisfies Record<string, TextStyle>;

export type TypeVariant = keyof typeof type;

// ---------------------------------------------------------------------------
// Espaçamento, alvos, forma, movimento
// ---------------------------------------------------------------------------

export const spacing = { xs: 4, s: 8, m: 12, l: 16, xl: 24 } as const;
/** Margem lateral das páginas. */
export const gutter = 16;
/** Alvo de toque mínimo. */
export const touchTarget = 44;
/** Bordas de controles (botão secundário, campo, caixa). */
export const strokeWidth = 2;

export const motion = {
  quickMs: 100,
  fastMs: 167,
  pageMs: 250,
  /** Deslocamento da entrada de página. */
  pageRise: 20,
  /** Escala ao pressionar. */
  pressScale: 0.98,
} as const;

export type Theme = Palette & {
  type: typeof type;
  spacing: typeof spacing;
  gutter: number;
  radius: 0;
  space: (n: number) => number;
};

function withTokens(p: Palette): Theme {
  return { ...p, type, spacing, gutter, radius: 0, space: (n: number) => n * 8 };
}

const darkTheme = withTokens(darkPalette);
const lightTheme = withTokens(lightPalette);

/** Tema estático (escuro) para código fora de componentes. Componentes usam `useTheme()`. */
export const theme: Theme = darkTheme;

/** Tema do sistema; escuro quando indefinido. */
export function useTheme(): Theme {
  const scheme = useColorScheme();
  return scheme === 'light' ? lightTheme : darkTheme;
}

// ---------------------------------------------------------------------------
// Semântica do domínio (mantém as funções exportadas originais)
// ---------------------------------------------------------------------------

/** Cor do score (texto/linha) ajustada ao fundo do tema. */
export function scoreColor(score: number, t: Palette = theme): string {
  if (score >= 81) return t.danger;
  if (score >= 61) return t.warn;
  if (score >= 41) return forBackground(AMBAR, t.bg);
  if (score >= 21) return forBackground(ESSENTIALS.amarelo, t.bg);
  return t.primary;
}

export function actionColor(action: string, t: Palette = theme): string {
  switch (action) {
    case 'BLOCK': return t.danger;
    case 'SILENCE': return t.mute;
    case 'WARN': return t.warn;
    default: return t.primary;
  }
}

export function actionLabel(action: string): string {
  switch (action) {
    case 'BLOCK': return 'Bloqueada';
    case 'SILENCE': return 'Silenciada';
    case 'WARN': return 'Suspeita';
    case 'ALLOW': return 'Permitida';
    default: return action;
  }
}
