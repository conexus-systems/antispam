#!/usr/bin/env bash
# Valida a configuração de publicação Android (local, sem rede e sem credenciais).
#
# Uso: scripts/publish/validate.sh [--min-version-code N]
#
# Falha (exit 1) se:
#   - o package de app.json divergir de agents/apps/fleet.yml (slug antispam-br);
#   - o versionCode não for inteiro positivo, ou não for maior que --min-version-code;
#   - o profile production do eas.json usar IDs de teste do AdMob ou não gerar app-bundle;
#   - o guard de app.config.js deixar passar IDs de teste no profile production.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
FLEET="${FLEET_YML:-$ROOT/../agents/apps/fleet.yml}"
SLUG="antispam-br"
MIN_VERSION_CODE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --min-version-code) MIN_VERSION_CODE="${2:-}"; shift 2 ;;
    *) echo "::error::argumento desconhecido: $1" >&2; exit 2 ;;
  esac
done

fail() { echo "FAIL: $*" >&2; exit 1; }
ok() { echo "OK: $*"; }

cd "$ROOT"

# 1. Package e versionCode do app.json
read -r PACKAGE VERSION_CODE < <(node -e '
  const app = require("./app.json").expo;
  process.stdout.write(`${app.android.package} ${app.android.versionCode}\n`);
')

[[ -n "$PACKAGE" ]] || fail "android.package ausente em app.json"
ok "package em app.json: $PACKAGE"

[[ "$VERSION_CODE" =~ ^[1-9][0-9]*$ ]] || fail "versionCode inválido: '$VERSION_CODE' (esperado inteiro positivo)"
ok "versionCode: $VERSION_CODE"

if [[ -n "$MIN_VERSION_CODE" ]]; then
  [[ "$MIN_VERSION_CODE" =~ ^[0-9]+$ ]] || fail "--min-version-code deve ser inteiro"
  (( VERSION_CODE > MIN_VERSION_CODE )) || fail "versionCode $VERSION_CODE não é maior que o último publicado ($MIN_VERSION_CODE)"
  ok "versionCode $VERSION_CODE > último publicado $MIN_VERSION_CODE"
fi

# 2. Package deve coincidir com fleet.yml (bloco do slug)
[[ -f "$FLEET" ]] || fail "fleet.yml não encontrado em $FLEET (defina FLEET_YML)"
FLEET_PACKAGE="$(awk -v slug="$SLUG" '
  $0 ~ "slug: "slug"$" { inblock=1; next }
  inblock && /^  - slug:/ { inblock=0 }
  inblock && /package:/ { sub(/.*package: */, ""); sub(/ .*/, ""); print; exit }
' "$FLEET")"
[[ -n "$FLEET_PACKAGE" ]] || fail "slug '$SLUG' sem package em $FLEET"
[[ "$FLEET_PACKAGE" == "$PACKAGE" ]] || fail "package divergente: app.json=$PACKAGE, fleet.yml=$FLEET_PACKAGE"
ok "package coincide com fleet.yml"

# 3. Profile production do EAS: AAB e sem IDs de teste
node -e '
  const eas = require("./eas.json").build.production;
  if (eas.android?.buildType !== "app-bundle") {
    console.error("FAIL: profile production não gera app-bundle (AAB)"); process.exit(1);
  }
  const testIds = String(eas.env?.EXPO_PUBLIC_ADMOB_USE_TEST_IDS ?? "false").trim();
  if (testIds === "true") {
    console.error("FAIL: profile production com EXPO_PUBLIC_ADMOB_USE_TEST_IDS=true"); process.exit(1);
  }
' || exit 1
ok "profile production gera AAB e não usa IDs de teste"

# 4. Guard de app.config.js: com profile production e IDs de teste, deve lançar erro
GUARD_OUT="$(EAS_BUILD_PROFILE=production EXPO_PUBLIC_ADMOB_USE_TEST_IDS=true \
  node -e 'require("./app.config.js")({ config: { plugins: [] } })' 2>&1 || true)"
grep -q "não é permitido no profile production" <<<"$GUARD_OUT" \
  || fail "guard de app.config.js não bloqueou IDs de teste em production"
ok "guard bloqueia IDs de teste em production"

# 5. Guard sem IDs de teste e sem unidades: deve passar
node -e '
  process.env.EAS_BUILD_PROFILE = "production";
  process.env.EXPO_PUBLIC_ADMOB_USE_TEST_IDS = "false";
  delete process.env.EXPO_PUBLIC_ADMOB_BANNER_ID;
  delete process.env.EXPO_PUBLIC_ADMOB_INTERSTITIAL_ID;
  require("./app.config.js")({ config: { plugins: [] } });
' || fail "guard de app.config.js bloqueou configuração válida de production"
ok "guard aceita configuração válida de production"

echo "PASS: validação concluída ($PACKAGE, versionCode $VERSION_CODE)"
