/**
 * Tokens W10M do AntiSpam BR — fonte única de cor de destaque, preferências de aparência e
 * validação de cor. Puro (sem React nem armazenamento): testável em Node.
 *
 * Paletas (claro/escuro), tipografia e movimento continuam em `src/ui/theme.ts`, que importa daqui.
 */

/** Cor de marca do app (fleet.yml › brand.primary). Padrão do destaque. */
export const BRAND_PRIMARY = '#DC2626';

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

export type AccentChoice = { readonly id: string; readonly label: string; readonly hex: string };

/** Opções da grade de cor de destaque: marca primeiro, depois as essenciais. */
export const ACCENT_CHOICES: readonly AccentChoice[] = [
  { id: 'brand', label: 'Vermelho do AntiSpam (padrão)', hex: BRAND_PRIMARY },
  { id: 'azul', label: 'Azul', hex: ESSENTIALS.azul },
  { id: 'ciano', label: 'Ciano', hex: ESSENTIALS.ciano },
  { id: 'verde', label: 'Verde', hex: ESSENTIALS.verde },
  { id: 'lima', label: 'Lima', hex: ESSENTIALS.lima },
  { id: 'laranja', label: 'Laranja', hex: ESSENTIALS.laranja },
  { id: 'amarelo', label: 'Amarelo', hex: ESSENTIALS.amarelo },
  { id: 'magenta', label: 'Magenta', hex: ESSENTIALS.magenta },
  { id: 'roxo', label: 'Roxo', hex: ESSENTIALS.roxo },
  { id: 'cinza', label: 'Cinza', hex: ESSENTIALS.cinza },
];

export const THEME_PREFS = ['system', 'dark', 'light'] as const;
export type ThemePref = (typeof THEME_PREFS)[number];

export type Appearance = {
  theme: ThemePref;
  /** Cor de destaque em `#RRGGBB` maiúsculo. */
  accent: string;
  /** "Reduzir animações" do próprio app (somado ao do sistema). */
  reduceMotion: boolean;
};

export const DEFAULT_APPEARANCE: Appearance = { theme: 'system', accent: BRAND_PRIMARY, reduceMotion: false };

/**
 * Aceita `#RRGGBB`, `RRGGBB` e `#RGB` (ex.: `1ba1e2`, `#F80`). Devolve `#RRGGBB` em maiúsculas
 * ou `null` quando o texto não é uma cor.
 */
export function normalizeHex(input: string): string | null {
  const raw = input.trim().replace(/^#/, '');
  if (/^[0-9a-fA-F]{3}$/.test(raw)) {
    return `#${raw
      .split('')
      .map((ch) => ch + ch)
      .join('')
      .toUpperCase()}`;
  }
  if (/^[0-9a-fA-F]{6}$/.test(raw)) return `#${raw.toUpperCase()}`;
  return null;
}

/** Cor de destaque válida ou o padrão da marca. */
export function resolveAccent(saved: unknown): string {
  if (typeof saved !== 'string') return BRAND_PRIMARY;
  return normalizeHex(saved) ?? BRAND_PRIMARY;
}

/** Valida preferências lidas do armazenamento; qualquer valor inválido cai no padrão. */
export function sanitizeAppearance(saved: Partial<Appearance> | null | undefined): Appearance {
  const theme = (THEME_PREFS as readonly string[]).includes(saved?.theme as string)
    ? (saved!.theme as ThemePref)
    : DEFAULT_APPEARANCE.theme;
  return {
    theme,
    accent: resolveAccent(saved?.accent),
    reduceMotion: saved?.reduceMotion === true,
  };
}
