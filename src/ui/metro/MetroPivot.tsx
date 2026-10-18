/**
 * Pivô W10M: títulos grandes e leves em linha (TITLE); o ativo em texto normal, os outros em secundário.
 * Toque no título ou deslize horizontal na página (ScrollView paginado; slide de 250 ms).
 * Acessível como lista de abas ("Regras, aba 2 de 4, selecionada"). Sem animação com "reduzir movimento".
 */
import React, { useEffect, useRef, useState } from 'react';
import { Pressable, ScrollView, useWindowDimensions, View, type NativeScrollEvent, type NativeSyntheticEvent, type ScrollViewInstance } from 'react-native';
import { spacing, useTheme } from '../theme';
import { useReduceMotion } from './motion';
import { MetroText } from './MetroText';

export type PivotPage = { key: string; title: string; render: () => React.ReactNode };

export function MetroPivot({ pages, initialIndex = 0, onChange }: { pages: PivotPage[]; initialIndex?: number; onChange?: (i: number) => void }) {
  const t = useTheme();
  const reduce = useReduceMotion();
  const { width } = useWindowDimensions();
  const [index, setIndex] = useState(initialIndex);
  const pager = useRef<ScrollViewInstance>(null);
  const strip = useRef<ScrollViewInstance>(null);
  const headerX = useRef<number[]>([]);

  useEffect(() => {
    const x = headerX.current[index];
    if (x != null) strip.current?.scrollTo({ x: Math.max(0, x - t.gutter), animated: !reduce });
  }, [index, reduce, t.gutter]);

  function select(i: number) {
    setIndex(i);
    onChange?.(i);
    pager.current?.scrollTo({ x: i * width, animated: !reduce });
  }

  function onMomentumEnd(e: NativeSyntheticEvent<NativeScrollEvent>) {
    const i = Math.round(e.nativeEvent.contentOffset.x / width);
    if (i !== index && i >= 0 && i < pages.length) {
      setIndex(i);
      onChange?.(i);
    }
  }

  return (
    <View style={{ flex: 1 }}>
      <ScrollView
        ref={strip}
        horizontal
        showsHorizontalScrollIndicator={false}
        accessibilityRole="tablist"
        contentContainerStyle={{ paddingHorizontal: t.gutter, gap: spacing.l }}
      >
        {pages.map((p, i) => (
          <Pressable
            key={p.key}
            onLayout={(e) => { headerX.current[i] = e.nativeEvent.layout.x; }}
            onPress={() => select(i)}
            accessibilityRole="tab"
            accessibilityLabel={`${p.title}, aba ${i + 1} de ${pages.length}`}
            accessibilityState={{ selected: i === index }}
            style={{ minHeight: 44, justifyContent: 'center', paddingBottom: spacing.xs }}
          >
            <MetroText variant="title" tone={i === index ? 'text' : 'muted'}>
              {p.title}
            </MetroText>
          </Pressable>
        ))}
      </ScrollView>
      <ScrollView
        ref={pager}
        horizontal
        pagingEnabled
        showsHorizontalScrollIndicator={false}
        onMomentumScrollEnd={onMomentumEnd}
        contentOffset={{ x: initialIndex * width, y: 0 }}
        style={{ flex: 1 }}
      >
        {pages.map((p) => (
          <ScrollView
            key={p.key}
            style={{ width }}
            contentContainerStyle={{ paddingHorizontal: t.gutter, paddingTop: spacing.l, paddingBottom: spacing.xl * 2, gap: spacing.xl }}
            keyboardShouldPersistTaps="handled"
          >
            {p.render()}
          </ScrollView>
        ))}
      </ScrollView>
    </View>
  );
}
