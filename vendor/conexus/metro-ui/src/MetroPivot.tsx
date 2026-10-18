import { useEffect, useRef, useState, type ComponentRef, type ReactNode } from "react";
import { Pressable, ScrollView, StyleSheet, View, type GestureResponderEvent, type StyleProp, type ViewStyle } from "react-native";

import { isHorizontalSwipe } from "./layout";
import { MetroSlideIn } from "./motion";
import { MetroText } from "./MetroText";
import { useMetro } from "./theme";
import { minTouch, pivotDescription, pivotStep, spacing } from "./tokens";

export type PivotSection = { readonly key: string; readonly title: string; readonly accessibilityLabel?: string };

type Props = {
  readonly sections: readonly PivotSection[];
  readonly selectedKey: string;
  readonly onSelect: (key: string) => void;
  /** Nome do app em maiúsculas acima dos títulos. */
  readonly kicker?: string;
  readonly trailing?: ReactNode;
  readonly style?: StyleProp<ViewStyle>;
};

/**
 * Cabeçalhos do pivô W10M: títulos grandes e leves em linha; o ativo na cor do texto com barra de 2 dp no
 * destaque, os outros em secundário. Tocar no título é a alternativa acessível ao deslize (lista de abas).
 */
export function MetroPivot({ sections, selectedKey, onSelect, kicker, trailing, style }: Props) {
  const { c, accent } = useMetro();
  const scroll = useRef<ComponentRef<typeof ScrollView>>(null);
  const offsets = useRef<Record<string, number>>({});

  useEffect(() => {
    const x = offsets.current[selectedKey];
    if (x != null) scroll.current?.scrollTo({ x: Math.max(0, x - spacing.sm), animated: true });
  }, [selectedKey]);

  return (
    <View style={[styles.header, style]}>
      {(kicker || trailing) && (
        <View style={styles.kickerRow}>
          {kicker ? <MetroText variant="kicker">{kicker}</MetroText> : <View />}
          {trailing}
        </View>
      )}
      <ScrollView
        ref={scroll}
        horizontal
        showsHorizontalScrollIndicator={false}
        accessibilityRole="tablist"
        contentContainerStyle={styles.titles}
      >
        {sections.map((section, index) => {
          const current = section.key === selectedKey;
          return (
            <Pressable
              key={section.key}
              accessibilityRole="tab"
              accessibilityState={{ selected: current }}
              accessibilityLabel={pivotDescription(section.accessibilityLabel ?? section.title, index, sections.length, current)}
              onLayout={(event) => {
                offsets.current[section.key] = event.nativeEvent.layout.x;
              }}
              onPress={() => onSelect(section.key)}
              style={({ pressed }) => [styles.tab, pressed && { backgroundColor: c.pressed }]}
            >
              <MetroText variant="title" tone={current ? "text" : "muted"} numberOfLines={1}>
                {section.title}
              </MetroText>
              <View style={[styles.bar, { backgroundColor: current ? accent.text : "transparent" }]} />
            </Pressable>
          );
        })}
      </ScrollView>
    </View>
  );
}

type SwipeProps = {
  readonly onPrevious?: () => void;
  readonly onNext?: () => void;
  readonly children: ReactNode;
  readonly style?: StyleProp<ViewStyle>;
};

/** Deslize horizontal claro no conteúdo troca de seção do pivô; rolagem vertical e toques passam direto. */
export function SwipeSections({ onPrevious, onNext, children, style }: SwipeProps) {
  const start = useRef({ x: 0, y: 0 });
  const delta = (event: GestureResponderEvent) => ({
    dx: event.nativeEvent.pageX - start.current.x,
    dy: event.nativeEvent.pageY - start.current.y,
  });
  return (
    <View
      style={[{ flex: 1 }, style]}
      onStartShouldSetResponderCapture={(event) => {
        start.current = { x: event.nativeEvent.pageX, y: event.nativeEvent.pageY };
        return false;
      }}
      onMoveShouldSetResponder={(event) => {
        const { dx, dy } = delta(event);
        return isHorizontalSwipe(dx, dy);
      }}
      onResponderTerminationRequest={() => true}
      onResponderRelease={(event) => {
        const { dx, dy } = delta(event);
        if (!isHorizontalSwipe(dx, dy, 60)) return;
        if (dx < 0) onNext?.();
        else onPrevious?.();
      }}
    >
      {children}
    </View>
  );
}

export type MetroPivotPage = PivotSection & { readonly render: () => ReactNode };

type PagerProps = {
  readonly pages: readonly MetroPivotPage[];
  /** Controlado: chave da seção atual. Sem ela o pager guarda o próprio estado. */
  readonly selectedKey?: string;
  readonly initialKey?: string;
  readonly onChange?: (key: string) => void;
  readonly kicker?: string;
  readonly trailing?: ReactNode;
  /** Entre os títulos e a página (ex.: filtro, faixa de aviso). */
  readonly header?: ReactNode;
  /** Deslize circular: da última volta à primeira, como no W10M. Padrão: true. */
  readonly wrap?: boolean;
  readonly style?: StyleProp<ViewStyle>;
};

/**
 * Pivô completo: títulos + página atual; deslize horizontal troca de seção e a nova página entra do lado de onde
 * veio (sem animação com "reduzir movimento"). Só a página atual é montada.
 */
export function MetroPivotPager({ pages, selectedKey, initialKey, onChange, kicker, trailing, header, wrap = true, style }: PagerProps) {
  const [own, setOwn] = useState(initialKey ?? pages[0]?.key ?? "");
  const current = selectedKey ?? own;
  const index = Math.max(0, pages.findIndex((page) => page.key === current));
  const direction = useRef(0);

  const select = (key: string, dir?: number) => {
    if (key === current) return;
    const nextIndex = pages.findIndex((page) => page.key === key);
    direction.current = dir ?? Math.sign(nextIndex - index);
    if (selectedKey === undefined) setOwn(key);
    onChange?.(key);
  };
  const step = (dir: number) => {
    const count = pages.length;
    if (count <= 1) return;
    const next = index + dir;
    if (!wrap && (next < 0 || next >= count)) return;
    select(pages[pivotStep(index, count, dir)].key, dir);
  };
  const page = pages[index];

  return (
    <View style={[{ flex: 1 }, style]}>
      <MetroPivot sections={pages} selectedKey={page?.key ?? ""} onSelect={(key) => select(key)} kicker={kicker} trailing={trailing} />
      {header}
      <SwipeSections onNext={() => step(1)} onPrevious={() => step(-1)}>
        {page ? (
          <MetroSlideIn direction={direction.current} replayKey={page.key}>
            {page.render()}
          </MetroSlideIn>
        ) : null}
      </SwipeSections>
    </View>
  );
}

/** Tipos mínimos do tabBar do React Navigation, sem importar o pacote. */
type TabRoute = { readonly key: string; readonly name: string };
type TabOptions = { readonly title?: string; readonly tabBarAccessibilityLabel?: string; readonly href?: unknown };
type TabNavigation = {
  readonly navigate: (name: string) => void;
  readonly emit: (event: { type: "tabPress"; target: string; canPreventDefault: true }) => { defaultPrevented: boolean };
};
type TabBarLikeProps = {
  readonly state: { readonly index: number; readonly routes: readonly TabRoute[] };
  readonly descriptors: Record<string, { readonly options: object }>;
  readonly navigation: unknown;
  readonly insets?: { readonly top: number };
};

/**
 * Abas do Expo Router desenhadas como pivô W10M no topo.
 * Use com `tabBar={(props) => <MetroPivotTabBar {...props} />}` e `tabBarPosition: "top"`.
 */
export function MetroPivotTabBar({
  state,
  descriptors,
  navigation,
  insets,
  kicker,
  hidden = [],
}: TabBarLikeProps & { readonly kicker?: string; readonly hidden?: readonly string[] }) {
  const { c } = useMetro();
  const nav = navigation as TabNavigation;
  const options = (key: string) => (descriptors[key]?.options ?? {}) as TabOptions;
  const routes = state.routes.filter((route) => !hidden.includes(route.name) && options(route.key).href !== null);
  const sections = routes.map((route) => ({
    key: route.key,
    title: options(route.key).title ?? route.name,
    accessibilityLabel: options(route.key).tabBarAccessibilityLabel,
  }));
  const current = state.routes[state.index]?.key ?? "";
  const onSelect = (key: string) => {
    const route = state.routes.find((r) => r.key === key);
    if (!route) return;
    const event = nav.emit({ type: "tabPress", target: route.key, canPreventDefault: true });
    if (!event.defaultPrevented && key !== current) nav.navigate(route.name);
  };
  return (
    <View style={{ backgroundColor: c.bg, paddingTop: insets?.top ?? 0 }}>
      <MetroPivot sections={sections} selectedKey={current} onSelect={onSelect} kicker={kicker} />
    </View>
  );
}

const styles = StyleSheet.create({
  header: { paddingTop: spacing.sm },
  kickerRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: spacing.screen,
    minHeight: 28,
  },
  titles: { paddingHorizontal: spacing.xs },
  tab: { minHeight: minTouch, justifyContent: "flex-end", paddingHorizontal: spacing.sm, paddingTop: 2 },
  bar: { alignSelf: "stretch", height: 2, marginTop: spacing.xs },
});
