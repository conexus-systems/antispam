#!/usr/bin/env bash
# Gera SHA-256 de um artefato (AAB/APK) e grava ao lado dele um arquivo .sha256.
#
# Uso: scripts/publish/checksum.sh <arquivo>
# Saída: <arquivo>.sha256 no formato do sha256sum (verificável com `sha256sum -c`).
set -euo pipefail

FILE="${1:-}"
[[ -n "$FILE" ]] || { echo "Uso: $0 <arquivo>" >&2; exit 2; }
[[ -f "$FILE" ]] || { echo "FAIL: arquivo não encontrado: $FILE" >&2; exit 1; }
[[ -s "$FILE" ]] || { echo "FAIL: arquivo vazio: $FILE" >&2; exit 1; }

DIR="$(cd "$(dirname "$FILE")" && pwd)"
BASE="$(basename "$FILE")"

(cd "$DIR" && sha256sum "$BASE" > "$BASE.sha256")
(cd "$DIR" && sha256sum -c "$BASE.sha256" >/dev/null) || { echo "FAIL: verificação do checksum falhou" >&2; exit 1; }

echo "OK: $DIR/$BASE.sha256"
cat "$DIR/$BASE.sha256"
