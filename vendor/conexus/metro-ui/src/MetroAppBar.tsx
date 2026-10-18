import { useState, type ReactNode } from "react";
import { Pressable, StyleSheet, View, type StyleProp, type ViewStyle } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { MetroIcon, renderIcon, type MetroIconSource } from "./MetroIcon";
import { MetroMenu, type MetroMenuItem } from "./MetroMenu";
import { MetroText } from "./MetroText";
import { useMetro } from "./theme";
import { minTouch, spacing } from "./tokens";

export const APP_BAR_HEIGHT = 64;

export type MetroAppBarButton = {
  /** Chave estável (padrão: o rótulo). */
  readonly key?: string;
  readonly icon: MetroIconSource;
  readonly label: string;
  readonly onPress: () => void;
  readonly onLongPress?: () => void;
  /** Destino atual (barra usada como abas): ícone e rótulo no destaque. */
  readonly active?: boolean;
  readonly disabled?: boolean;
  readonly accessibilityLabel?: string;
  readonly testID?: string;
};

type Props = {
  /** Até 4 comandos principais (círculos finos com rótulo). */
  readonly buttons: readonly MetroAppBarButton[];
  /** Comandos secundários no "..." (menu que sobe da barra). */
  readonly menu?: readonly MetroMenuItem[];
  /** "always" (padrão) mostra os rótulos; "expanded" só depois de tocar no "...", como no W10M original. */
  readonly labels?: "always" | "expanded";
  /** Semântica de abas (tablist/tab) em vez de botões. */
  readonly asTabs?: boolean;
  /** Soma a área segura de baixo (padrão: true). */
  readonly safeArea?: boolean;
  readonly style?: StyleProp<ViewStyle>;
};

/**
 * App bar W10M: faixa chrome plana embaixo, comandos em círculos de traço fino com rótulo (destaque só no ativo) e
 * um "..." que expande os rótulos e abre os comandos secundários.
 */
export function MetroAppBar({ buttons, menu = [], labels = "always", asTabs = false, safeArea = true, style }: Props) {
  const { c, accent } = useMetro();
  const insets = useSafeAreaInsets();
  const [expanded, setExpanded] = useState(false);
  const bottom = safeArea ? insets.bottom : 0;
  const showLabels = labels === "always" || expanded;
  const hasMore = menu.length > 0 || labels === "expanded";

  return (
    <View
      accessibilityRole={asTabs ? "tablist" : "toolbar"}
      style={[{ backgroundColor: c.chrome, paddingBottom: bottom }, style]}
    >
      <View style={styles.row}>
        <View style={[styles.commands, hasMore && styles.commandsWithMore]}>
          {buttons.slice(0, 4).map((button) => {
            const ink = button.disabled ? c.disabled : button.active ? accent.text : c.text;
            return (
              <Pressable
                key={button.key ?? button.label}
                onPress={button.onPress}
                onLongPress={button.onLongPress}
                disabled={button.disabled}
                testID={button.testID}
                accessibilityRole={asTabs ? "tab" : "button"}
                accessibilityLabel={button.accessibilityLabel ?? button.label}
                accessibilityState={{ selected: !!button.active, disabled: !!button.disabled }}
                style={styles.command}
              >
                {({ pressed }) => (
                  <>
                    <View style={[styles.circle, { borderColor: ink }, pressed && { backgroundColor: ink }]}>
                      {renderIcon(button.icon, { size: 18, color: pressed ? c.chrome : ink })}
                    </View>
                    {showLabels ? (
                      <MetroText variant="caption" color={ink} numberOfLines={1} style={styles.label}>
                        {button.label}
                      </MetroText>
                    ) : null}
                  </>
                )}
              </Pressable>
            );
          })}
        </View>
        {hasMore ? (
          <Pressable
            onPress={() => setExpanded((open) => !open)}
            accessibilityRole="button"
            accessibilityLabel="Mais opções"
            accessibilityState={{ expanded }}
            style={styles.more}
          >
            <MetroIcon name="ellipsis-horizontal" size={22} />
          </Pressable>
        ) : null}
      </View>
      {menu.length ? (
        <MetroMenu
          visible={expanded}
          onClose={() => setExpanded(false)}
          items={menu}
          position="bottom"
          bottomOffset={APP_BAR_HEIGHT + bottom}
        />
      ) : null}
    </View>
  );
}

/** Tipos mínimos do tabBar do React Navigation/expo-router, sem importar o pacote. */
type TabRoute = { readonly key: string; readonly name: string; readonly params?: object };
type TabIcon = (props: { focused: boolean; color: string; size: number }) => ReactNode;
type TabOptions = {
  readonly title?: string;
  readonly tabBarLabel?: unknown;
  readonly tabBarIcon?: TabIcon;
  readonly tabBarAccessibilityLabel?: string;
  readonly tabBarButtonTestID?: string;
  readonly href?: unknown;
};
type TabNavigation = {
  readonly navigate: (name: string, params?: object) => void;
  readonly emit: (event: { type: string; target: string; canPreventDefault?: boolean }) => { defaultPrevented: boolean };
};
export type MetroTabBarProps = {
  readonly state: { readonly index: number; readonly routes: readonly TabRoute[] };
  readonly descriptors: Record<string, { readonly options: object }>;
  readonly navigation: unknown;
  readonly insets?: { readonly bottom: number; readonly top: number };
};

/**
 * Abas do expo-router como app bar W10M: `tabBar={(props) => <MetroAppBarTabBar {...props} />}`.
 * Ícone vem de `icons[route.name]` ou do `tabBarIcon` da rota (recebe a cor certa); rotas com `href: null` somem.
 */
export function MetroAppBarTabBar({
  state,
  descriptors,
  navigation,
  icons = {},
  menu,
  labels,
  hidden = [],
}: MetroTabBarProps & {
  readonly icons?: Readonly<Record<string, MetroIconSource>>;
  readonly menu?: readonly MetroMenuItem[];
  readonly labels?: "always" | "expanded";
  readonly hidden?: readonly string[];
}) {
  const { c, accent } = useMetro();
  const nav = navigation as TabNavigation;
  const options = (key: string) => (descriptors[key]?.options ?? {}) as TabOptions;
  const current = state.routes[state.index]?.key;
  const buttons = state.routes
    .filter((route) => !hidden.includes(route.name) && options(route.key).href !== null)
    .map((route): MetroAppBarButton => {
      const opts = options(route.key);
      const focused = route.key === current;
      const label = typeof opts.tabBarLabel === "string" ? opts.tabBarLabel : (opts.title ?? route.name);
      return {
        key: route.key,
        label,
        active: focused,
        accessibilityLabel: opts.tabBarAccessibilityLabel,
        testID: opts.tabBarButtonTestID,
        icon: icons[route.name] ?? opts.tabBarIcon?.({ focused, color: focused ? accent.text : c.text, size: 18 }) ?? "ellipse-outline",
        onPress: () => {
          const event = nav.emit({ type: "tabPress", target: route.key, canPreventDefault: true });
          if (!focused && !event.defaultPrevented) nav.navigate(route.name, route.params);
        },
        onLongPress: () => nav.emit({ type: "tabLongPress", target: route.key }),
      };
    });
  return <MetroAppBar buttons={buttons} menu={menu} labels={labels} asTabs />;
}

const styles = StyleSheet.create({
  row: { height: APP_BAR_HEIGHT, flexDirection: "row", alignItems: "flex-start" },
  commands: { flex: 1, flexDirection: "row", justifyContent: "center", paddingTop: spacing.xs },
  commandsWithMore: { paddingLeft: minTouch },
  command: { minWidth: 64, minHeight: minTouch, alignItems: "center", paddingHorizontal: spacing.xs },
  // Círculos dos comandos: exceção ao "sem cantos", como no original.
  circle: { width: 32, height: 32, borderRadius: 16, borderWidth: 1.5, alignItems: "center", justifyContent: "center" },
  label: { marginTop: 2, fontSize: 11, lineHeight: 14 },
  more: { width: minTouch, height: APP_BAR_HEIGHT, alignItems: "center", paddingTop: spacing.sm },
});
