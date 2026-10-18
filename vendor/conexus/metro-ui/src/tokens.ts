/**
 * Tokens W10M (Windows 10 Mobile), portados 1:1 do Conexus Launcher (ui/Theme.kt, ui/Accent.kt, ui/Type.kt,
 * ui/Motion.kt; docs/W10M_DESIGN_SYSTEM.md). Lógica pura, sem React Native: testável em Jest.
 * Origem: condo_nexus/mobile/components/metro/tokens.ts (a cópia mais completa), unificada com as demais.
 *
 * Regras: superfícies planas (raio 0, sem sombra, sem gradiente), tipografia leve faz a hierarquia,
 * a cor de destaque só marca função (seleção, links, controles ativos, foco, pivô, grupo).
 */

export type Scheme = "dark" | "light";

/** Segue o tema do sistema; escuro é o padrão quando o sistema não informa. */
export function resolveScheme(system: string | null | undefined): Scheme {
  return system === "light" ? "light" : "dark";
}

type Rgb = readonly [number, number, number];

export function parseHex(hex: string): Rgb | null {
  const match = /^#?([0-9a-f]{6})$/i.exec(hex.trim());
  if (!match) return null;
  const value = parseInt(match[1], 16);
  return [(value >> 16) & 0xff, (value >> 8) & 0xff, value & 0xff];
}

export function formatHex([r, g, b]: Rgb): string {
  return "#" + [r, g, b].map((c) => c.toString(16).padStart(2, "0").toUpperCase()).join("");
}

export function luminance(hex: string): number {
  const rgb = parseHex(hex) ?? [0, 0, 0];
  const [r, g, b] = rgb.map((c) => {
    const v = c / 255;
    return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

/** Contraste WCAG entre duas cores opacas. */
export function contrast(a: string, b: string): number {
  const la = luminance(a);
  const lb = luminance(b);
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
}

function scale(rgb: Rgb, factor: number): Rgb {
  return rgb.map((c) => Math.max(0, Math.min(255, Math.floor(c * factor)))) as unknown as Rgb;
}

/** Accent.mix: `amount` (0–1) de `other` misturado em `rgb`. */
function mix(rgb: Rgb, other: Rgb, amount: number): Rgb {
  return rgb.map((c, i) => Math.max(0, Math.min(255, Math.floor(c * (1 - amount) + other[i] * amount)))) as unknown as Rgb;
}

/** Accent.forWhiteText: escurece em passos de 5% até o texto branco passar 4,5:1. */
export function forWhiteText(hex: string): string {
  let current = parseHex(hex);
  if (!current) return hex;
  for (let i = 0; i < 40; i++) {
    if (contrast(formatHex(current), "#FFFFFF") >= 4.5) break;
    current = scale(current, 0.95);
  }
  return formatHex(current);
}

/**
 * Accent.forBackground: a cor ajustada até passar 4,5:1 como texto sobre `background` — misturada ao branco
 * (10% por passo) em fundo escuro, escurecida (×0,92) em fundo claro. Cores que já passam voltam iguais.
 */
export function forBackground(hex: string, background: string): string {
  let current = parseHex(hex);
  if (!current) return hex;
  const dark = luminance(background) < 0.5;
  for (let i = 0; i < 40; i++) {
    if (contrast(formatHex(current), background) >= 4.5) break;
    current = dark ? mix(current, [255, 255, 255], 0.1) : scale(current, 0.92);
  }
  return formatHex(current);
}

export function withAlpha(hex: string, alpha: number): string {
  const rgb = parseHex(hex);
  if (!rgb) return hex;
  return `rgba(${rgb[0]},${rgb[1]},${rgb[2]},${Math.round(alpha * 1000) / 1000})`;
}

/** As 10 cores essenciais do W10M (Personalização › Cores), na ordem do seletor. */
export const ESSENTIALS = [
  { name: "Azul", hex: "#0050EF" },
  { name: "Ciano", hex: "#1BA1E2" },
  { name: "Verde", hex: "#60A917" },
  { name: "Lima", hex: "#A4C400" },
  { name: "Laranja", hex: "#FA6800" },
  { name: "Amarelo", hex: "#E3C800" },
  { name: "Vermelho", hex: "#E51400" },
  { name: "Magenta", hex: "#D80073" },
  { name: "Roxo", hex: "#AA00FF" },
  { name: "Cinza", hex: "#647687" },
] as const;

export type Essential = (typeof ESSENTIALS)[number];

/** CIE L*a*b* (D65), para medir distância perceptual entre cores. */
function toLab(hex: string): readonly [number, number, number] {
  const [r, g, b] = (parseHex(hex) ?? [0, 0, 0]).map((c) => {
    const v = c / 255;
    return v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
  });
  const x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047;
  const y = 0.2126 * r + 0.7152 * g + 0.0722 * b;
  const z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883;
  const f = (t: number) => (t > 216 / 24389 ? Math.cbrt(t) : (24389 / 27 * t + 16) / 116);
  return [116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z))];
}

/** Distância ΔE76 entre duas cores. */
export function deltaE(a: string, b: string): number {
  const [l1, a1, b1] = toLab(a);
  const [l2, a2, b2] = toLab(b);
  return Math.hypot(l1 - l2, a1 - a2, b1 - b2);
}

/** A essencial W10M perceptualmente mais próxima de `hex`. */
export function nearestEssential(hex: string): Essential {
  return ESSENTIALS.reduce((best, item) => (deltaE(hex, item.hex) < deltaE(hex, best.hex) ? item : best), ESSENTIALS[0]);
}

/** Destaque padrão do kit quando o app não passa um (Azul/Cobalto W10M). */
export const DEFAULT_ACCENT: string = ESSENTIALS[0].hex;

/**
 * Destaque a partir da cor de marca do app: a essencial W10M perceptualmente mais próxima (ΔE76).
 * Ex.: marca azul-marinho #1E3A5F → Cinza #647687.
 */
export function accentFromBrand(brandHex: string): string {
  return parseHex(brandHex) ? nearestEssential(brandHex).hex : DEFAULT_ACCENT;
}

export type Palette = {
  /** Fundo da página. */
  readonly bg: string;
  /** Superfície elevada: barras, menus, diálogos, faixa de status. */
  readonly chrome: string;
  /** Superfície de controle (trilho, botão secundário pressionado). */
  readonly surface: string;
  readonly text: string;
  /** Texto secundário (passa 4,5:1 nos dois temas). */
  readonly muted: string;
  readonly disabled: string;
  /** Divisória discreta. */
  readonly divider: string;
  /** Linha/célula pressionada. */
  readonly pressed: string;
  /** Fundo escurecido atrás de diálogos e menus (60%). */
  readonly scrim: string;
  /** Vermelho/verde/amarelo/ciano W10M ajustados para texto sobre `bg`. */
  readonly danger: string;
  readonly success: string;
  readonly warning: string;
  readonly info: string;
  /** Preenchimento do botão destrutivo (texto branco). */
  readonly dangerFill: string;
  /** Texto sobre o destaque e sobre o vermelho. */
  readonly onAccent: string;
  /** Campo de texto em foco: fundo branco, texto preto, nos dois temas. */
  readonly fieldFocusBg: string;
  readonly fieldFocusText: string;
  /** Placeholder dentro do campo em foco (fundo branco). */
  readonly fieldFocusMuted: string;
};

const W10M_RED = "#E51400";
const W10M_GREEN = "#60A917";
const W10M_YELLOW = "#E3C800";
const W10M_CYAN = "#1BA1E2";

function palette(scheme: Scheme): Palette {
  const dark = scheme === "dark";
  const bg = dark ? "#000000" : "#FFFFFF";
  return {
    bg,
    chrome: dark ? "#1A1A1A" : "#F2F2F2",
    surface: dark ? "#2B2B2B" : "#E6E6E6",
    text: dark ? "#FFFFFF" : "#000000",
    muted: dark ? "#CCCCCC" : "#505050",
    disabled: dark ? "rgba(255,255,255,0.4)" : "rgba(0,0,0,0.4)",
    divider: dark ? "#333333" : "#CCCCCC",
    pressed: dark ? "rgba(255,255,255,0.2)" : "rgba(0,0,0,0.13)",
    scrim: "rgba(0,0,0,0.6)",
    danger: forBackground(W10M_RED, bg),
    success: forBackground(W10M_GREEN, bg),
    warning: forBackground(W10M_YELLOW, bg),
    info: forBackground(W10M_CYAN, bg),
    dangerFill: forWhiteText(W10M_RED),
    onAccent: "#FFFFFF",
    fieldFocusBg: "#FFFFFF",
    fieldFocusText: "#000000",
    fieldFocusMuted: "#666666",
  };
}

export const palettes: Record<Scheme, Palette> = { dark: palette("dark"), light: palette("light") };

export type AccentSet = {
  /** Preenchimento com texto branco: botão primário, tile, controle ligado, seleção. */
  readonly fill: string;
  /** Texto e linhas sobre o fundo da página: links, barra do pivô, foco, cabeçalho de grupo. */
  readonly text: string;
};

export function accentSet(accent: string, scheme: Scheme): AccentSet {
  const fill = forWhiteText(accent);
  return { fill, text: forBackground(accent, palettes[scheme].bg) };
}

/** Grade de 4 (4/8/12/16/24/32); margem lateral da página 12–16 dp. */
export const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 32,
  /** Margem lateral da página. */
  screen: 12,
  /** Margem de cada lado do tile (4 dp entre dois tiles). */
  tileGap: 2,
} as const;

/** Alvo de toque mínimo (≥ 44 dp; o launcher usa 48). */
export const minTouch = 48;

/** Linhas de borda W10M: controles 2 dp, painéis 1 dp. */
export const borders = { control: 2, panel: 1 } as const;

/** Sem cantos arredondados em lugar nenhum. */
export const radius = 0;

/** Pesos: 300 = sans-serif-light, 400 = regular, 600 = semibold (fonte do sistema; Segoe não pode ser embarcada). */
export const weights = { light: "300", regular: "400", semibold: "600" } as const;

/** Escala tipográfica W10M (Type.Style), em sp: respeita a escala de fonte do sistema. */
export const type = {
  display: { fontSize: 64, lineHeight: 72, fontWeight: weights.light },
  header: { fontSize: 34, lineHeight: 40, fontWeight: weights.light },
  title: { fontSize: 28, lineHeight: 34, fontWeight: weights.light },
  subtitle: { fontSize: 20, lineHeight: 26, fontWeight: weights.regular },
  group: { fontSize: 16, lineHeight: 22, fontWeight: weights.semibold },
  body: { fontSize: 15, lineHeight: 20, fontWeight: weights.regular },
  caption: { fontSize: 12, lineHeight: 16, fontWeight: weights.regular },
  kicker: { fontSize: 12, lineHeight: 16, fontWeight: weights.semibold, letterSpacing: 0.72, textTransform: "uppercase" },
} as const;

export type TypeStyle = keyof typeof type;

/** Atraso (ms) da linha `index` numa entrada escalonada (Motion.stagger do launcher). */
export function staggerDelay(index: number): number {
  return motion.staggerMs * Math.max(0, Math.min(index, motion.staggerMax));
}

/**
 * Tilt W10M: ponto tocado (relativo ao centro) → rotação em graus. Tocar à direita inclina a borda direita para
 * dentro (rotateY positivo), embaixo inclina a borda de baixo (rotateX negativo). Centro = só o empurrão.
 */
export function tiltAngles(x: number, y: number, width: number, height: number, max: number = motion.tiltDeg) {
  if (width <= 0 || height <= 0) return { rotateX: 0, rotateY: 0 };
  const nx = Math.max(-1, Math.min(1, (x - width / 2) / (width / 2)));
  const ny = Math.max(-1, Math.min(1, (y - height / 2) / (height / 2)));
  const round = (v: number) => Math.round(v * 100) / 100 || 0;
  return { rotateX: round(-ny * max), rotateY: round(nx * max) };
}

/** Movimento (Motion.kt + briefing): curto, desacelerado, desligável. */
export const motion = {
  quickMs: 100,
  fastMs: 167,
  pageMs: 250,
  flipMs: 400,
  /** Entrada de página: sobe 20 dp e aparece. */
  travel: 20,
  /** Atraso por linha numa entrada escalonada, limitado a `staggerMax` linhas. */
  staggerMs: 20,
  staggerMax: 8,
  /** Inclinação máxima do "tilt" W10M ao tocar perto da borda, em graus. */
  tiltDeg: 8,
  /** Empurrão do toque. */
  pressScale: 0.98,
  /** Curva desacelerada do launcher (PathInterpolator 0.1, 0.9, 0.2, 1). */
  easing: [0.1, 0.9, 0.2, 1] as const,
} as const;

/** "Ativado"/"Desativado" do interruptor W10M (MetroRules.toggleState). */
export function toggleState(on: boolean): string {
  return on ? "Ativado" : "Desativado";
}

/** Rótulo de aba do pivô para leitores de tela (MetroRules.pivotDescription). */
export function pivotDescription(title: string, index: number, count: number, selected: boolean): string {
  return `${title}, aba ${index + 1} de ${count}` + (selected ? ", selecionada" : "");
}

/** Passo circular do pivô pelo teclado (MetroRules.pivotStep). */
export function pivotStep(index: number, count: number, direction: number): number {
  if (count <= 0) return 0;
  return (((index + direction) % count) + count) % count;
}

export type DialogButtonStyle = "default" | "cancel" | "destructive";

/**
 * Ordem W10M dos botões do diálogo, da esquerda para a direita: ações primeiro (na ordem dada), "cancel" por
 * último (MetroRules.dialogButtons: primário, secundário, fechar).
 */
export function dialogButtonOrder<T extends { readonly style?: DialogButtonStyle }>(buttons: readonly T[]): T[] {
  return [...buttons.filter((b) => b.style !== "cancel"), ...buttons.filter((b) => b.style === "cancel")];
}
