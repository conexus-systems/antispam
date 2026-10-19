/** Lógica pura do kit (sem React Native), testável em Jest. */

/** S = 1×1, M = 2×2, W = 4×2, como os tiles pequeno, médio e largo do Início. */
export type TileSize = "S" | "M" | "W";

export const TILE_SPAN: Record<TileSize, { readonly columns: number; readonly rows: number }> = {
  S: { columns: 1, rows: 1 },
  M: { columns: 2, rows: 2 },
  W: { columns: 4, rows: 2 },
};

export type PlacedTile = { readonly index: number; readonly row: number; readonly column: number };

/** Primeiro encaixe linha a linha numa grade de 4 colunas, na ordem dada (StartTiles.pack). */
export function packTiles(sizes: readonly TileSize[], columns = 4): { placed: PlacedTile[]; rows: number } {
  const used: boolean[][] = [];
  const free = (row: number, column: number) => !used[row]?.[column];
  const placed: PlacedTile[] = [];
  let rows = 0;
  sizes.forEach((size, index) => {
    const span = TILE_SPAN[size];
    const width = Math.min(span.columns, columns);
    for (let row = 0; ; row++) {
      for (let column = 0; column + width <= columns; column++) {
        let fits = true;
        for (let r = row; r < row + span.rows && fits; r++)
          for (let c = column; c < column + width && fits; c++) fits = free(r, c);
        if (!fits) continue;
        for (let r = row; r < row + span.rows; r++) {
          used[r] = used[r] ?? [];
          for (let c = column; c < column + width; c++) used[r][c] = true;
        }
        placed.push({ index, row, column });
        rows = Math.max(rows, row + span.rows);
        return;
      }
    }
  });
  return { placed, rows };
}

/** Gesto horizontal só quando claramente horizontal, para não brigar com a rolagem vertical. */
export function isHorizontalSwipe(dx: number, dy: number, threshold = 24): boolean {
  return Math.abs(dx) > threshold && Math.abs(dx) > Math.abs(dy) * 2;
}
