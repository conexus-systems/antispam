import { Children, isValidElement, useState, type ReactElement, type ReactNode } from "react";
import { StyleSheet, View, type AccessibilityActionEvent, type StyleProp, type ViewStyle } from "react-native";

import { renderIcon, type MetroIconSource } from "./MetroIcon";
import { MetroText } from "./MetroText";
import { MetroPressable } from "./motion";
import { packTiles, TILE_SPAN, type TileSize } from "./layout";
import { useMetro } from "./theme";
import { spacing, type } from "./tokens";

export type TileAction = { readonly label: string; readonly onPress: () => void };

export type MetroTileProps = {
  readonly size?: TileSize;
  /** Nome no rodapé do tile (oculto no S, como no Iniciar). */
  readonly label?: string;
  /** Número ou texto curto leve no canto superior. */
  readonly value?: string;
  readonly big?: boolean;
  readonly icon?: MetroIconSource;
  /** Inclinação W10M na direção do toque (padrão: true). */
  readonly tilt?: boolean;
  readonly badge?: number | string;
  readonly onPress?: () => void;
  /** Mesmas ações do toque longo como ações do leitor de tela. */
  readonly actions?: readonly TileAction[];
  readonly onLongPress?: () => void;
  readonly accessibilityLabel: string;
  readonly accessibilityHint?: string;
  readonly testID?: string;
  readonly disabled?: boolean;
  readonly children?: ReactNode;
  readonly style?: StyleProp<ViewStyle>;
};

/** Live Tile W10M: quadrado plano na cor de destaque, texto branco, sem cantos nem sombra. */
export function MetroTile({
  size = "M",
  label,
  value,
  big,
  icon,
  badge,
  onPress,
  actions,
  onLongPress,
  accessibilityLabel,
  accessibilityHint,
  testID,
  disabled,
  children,
  tilt = true,
  style,
}: MetroTileProps) {
  const { c, accent } = useMetro();
  const small = size === "S";
  const onAction = (event: AccessibilityActionEvent) => actions?.find((a) => a.label === event.nativeEvent.actionName)?.onPress();
  return (
    <MetroPressable
      tilt={tilt}
      containerStyle={styles.container}
      accessibilityRole={onPress ? "button" : "summary"}
      accessibilityLabel={accessibilityLabel}
      accessibilityHint={accessibilityHint}
      accessibilityState={{ disabled: !!disabled }}
      accessibilityActions={actions?.map((a) => ({ name: a.label, label: a.label }))}
      onAccessibilityAction={actions ? onAction : undefined}
      onPress={onPress}
      onLongPress={onLongPress}
      disabled={disabled || (!onPress && !onLongPress)}
      testID={testID}
      style={[styles.tile, { backgroundColor: accent.fill }, style]}
    >
      {value != null && (
        <MetroText
          style={[small ? type.subtitle : big ? type.header : type.title, styles.value, { color: c.onAccent }]}
          numberOfLines={1}
          adjustsFontSizeToFit
        >
          {value}
        </MetroText>
      )}
      {icon != null && (
        <View style={styles.icon}>
          {renderIcon(icon, { size: small ? 24 : 40, tone: "onAccent" })}
        </View>
      )}
      {children}
      {!small && label ? (
        <MetroText variant="caption" style={[styles.label, { color: c.onAccent }]} numberOfLines={1}>
          {label}
        </MetroText>
      ) : null}
      {badge != null && badge !== 0 ? (
        <MetroText variant="caption" style={[styles.badge, { color: c.onAccent }]}>
          {badge}
        </MetroText>
      ) : null}
    </MetroPressable>
  );
}

type GridProps = {
  readonly children: ReactNode;
  readonly columns?: number;
  /** Teto da célula (110 dp, como o launcher), para tablets não gerarem tiles gigantes. */
  readonly maxCell?: number;
  readonly style?: StyleProp<ViewStyle>;
};

/** Grade de tiles do Iniciar: 4 colunas, encaixe S/M/W e 4 dp entre os tiles. */
export function MetroTileGrid({ children, columns = 4, maxCell = 110, style }: GridProps) {
  const [width, setWidth] = useState(0);
  const tiles = Children.toArray(children).filter(isValidElement) as ReactElement<MetroTileProps>[];
  const sizes = tiles.map((tile) => tile.props.size ?? "M");
  const { placed, rows } = packTiles(sizes, columns);
  const cell = width > 0 ? Math.min(width / columns, maxCell) : 0;
  const gap = spacing.tileGap;
  return (
    <View style={[{ width: "100%" }, style]} onLayout={(event) => setWidth(event.nativeEvent.layout.width)} accessibilityRole="none">
      {cell > 0 && (
        <View style={{ width: cell * columns, height: cell * rows, alignSelf: "center" }}>
          {placed.map(({ index, row, column }) => {
            const span = TILE_SPAN[sizes[index]];
            const spanColumns = Math.min(span.columns, columns);
            return (
              <View
                key={tiles[index].key ?? index}
                style={{
                  position: "absolute",
                  left: column * cell + gap,
                  top: row * cell + gap,
                  width: cell * spanColumns - gap * 2,
                  height: cell * span.rows - gap * 2,
                }}
              >
                {tiles[index]}
              </View>
            );
          })}
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  tile: { flex: 1, minHeight: 48, overflow: "hidden" },
  value: { paddingHorizontal: spacing.sm, paddingTop: spacing.xs },
  icon: { position: "absolute", top: 0, right: 0, bottom: 0, left: 0, alignItems: "center", justifyContent: "center" },
  label: { position: "absolute", left: spacing.sm, right: spacing.sm, bottom: 6 },
  badge: { position: "absolute", top: spacing.xs, right: spacing.sm },
});
