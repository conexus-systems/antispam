import { useEffect, useState, type ReactNode } from 'react';
import { Alert, Clipboard, View } from 'react-native';
import { useRouter } from 'expo-router';
import Constants from 'expo-constants';
import { spacing, useTheme } from '../../src/ui/theme';
import {
  MetroButton,
  MetroDialog,
  MetroListItem,
  MetroPage,
  MetroPivotPager,
  MetroRadio,
  MetroRadioGroup,
  MetroSection,
  MetroText,
  MetroTextField,
  MetroToggle,
} from '@conexus/metro-ui';
import { useConexusAccount } from '@conexus/account-expo';
import { AppScreen, Flush, MetroRow } from '../../src/ui/app';
import { AdBanner } from '../../src/ui/AdBanner';
import { adMobService } from '../../src/ads/adMob';
import {
  useAppState,
  setMode,
  setCustomConfig,
  upsertRule,
  removeRule,
  toggleList,
  getState,
  setState,
  setAppearance,
} from '../../src/app/store';
import { AccentSwatch } from '../../src/ui/metro/AccentSwatch';
import { ACCENT_CHOICES, BRAND_PRIMARY, normalizeHex } from '../../src/ui/metro/tokens';
import { importBackup, exportBackup } from '../../src/app/backup';
import { getDb } from '../../src/app/screening';
import { isCallScreeningRoleHeld, isNativeScreeningAvailable, openCallScreeningSettings, requestCallScreeningRole } from '../../src/app/nativeBridge';
import { HANDLE_0303_LABELS, type Handle0303 } from '../../src/core/phone/brazilRules';
import type { ProtectionMode } from '../../src/core/types';
import { FluentIcon } from '../../src/ui/metro/icons';

const APP_VERSION: string = Constants.expoConfig?.version ?? '';

/** Conteúdo de uma página do pivô: rolável, com o mesmo espaçamento das outras telas. */
function pivotPage(content: ReactNode) {
  return (
    <MetroPage scroll padded={false}>
      <View style={{ gap: spacing.xl, paddingBottom: spacing.xl * 2 }}>{content}</View>
    </MetroPage>
  );
}

const MODES: Array<{ id: ProtectionMode; label: string; desc: string }> = [
  { id: 'OFF', label: 'Desativado', desc: 'Não analisa nem bloqueia nada.' },
  { id: 'BASIC', label: 'Básico', desc: 'Bloqueia só spam de altíssima confiança.' },
  { id: 'BALANCED', label: 'Equilibrado', desc: 'Bloqueia spam conhecido e campanhas suspeitas. Recomendado.' },
  { id: 'AGGRESSIVE', label: 'Agressivo', desc: 'Mais restritivo. Maior risco de bloquear algo legítimo.' },
  { id: 'CUSTOM', label: 'Personalizado', desc: 'Você define limiares e o tratamento do 0303.' },
];

export default function Settings() {
  const router = useRouter();
  const theme = useTheme();
  const [confirmReset, setConfirmReset] = useState(false);
  const mode = useAppState((s) => s.mode);
  const config = useAppState((s) => s.config);
  const rules = useAppState((s) => s.rules);
  const allowlist = useAppState((s) => s.allowlist);
  const blacklist = useAppState((s) => s.blacklist);
  const ai = useAppState((s) => s.ai);
  const appearance = useAppState((s) => s.appearance);
  const history = useAppState((s) => s.history);
  const account = useConexusAccount();

  const [newRule, setNewRule] = useState({ name: '', prefix: '' });
  const [listInput, setListInput] = useState({ allow: '', block: '' });
  const [aiKeyInput, setAiKeyInput] = useState(ai.apiKey);
  const [roleHeld, setRoleHeld] = useState<boolean | null>(null);
  const [adPrivacyRequired, setAdPrivacyRequired] = useState(false);
  const [customHex, setCustomHex] = useState('');
  const [hexError, setHexError] = useState(false);

  useEffect(() => {
    let cancelled = false;
    if (!adMobService.isEnabled()) return;
    void adMobService.whenReady().then(() => {
      if (!cancelled) setAdPrivacyRequired(adMobService.isPrivacyOptionsRequired());
    });
    return () => {
      cancelled = true;
    };
  }, []);

  async function openAdPrivacy() {
    try {
      await adMobService.showPrivacyOptions();
    } catch {
      Alert.alert('Privacidade de anúncios', 'Não foi possível abrir as opções agora. Tente de novo mais tarde.');
    }
  }

  async function refreshRole() {
    if (isNativeScreeningAvailable()) setRoleHeld(await isCallScreeningRoleHeld());
  }
  void refreshRole();

  function addPrefixRule() {
    const name = newRule.name.trim() || `Prefixo ${newRule.prefix}`;
    if (!newRule.prefix.trim()) return;
    upsertRule({
      id: `rule-${Date.now().toString(36)}`,
      name,
      enabled: true,
      action: 'BLOCK',
      match: { kind: 'prefix', value: newRule.prefix.replace(/\s+/g, '') },
    });
    setNewRule({ name: '', prefix: '' });
  }

  function addToList(kind: 'allow' | 'block') {
    const value = (kind === 'allow' ? listInput.allow : listInput.block).trim();
    if (!value) return;
    toggleList(kind === 'allow' ? 'allowlist' : 'blacklist', value);
    setListInput((p) => ({ ...p, [kind]: '' }));
  }

  async function doExport() {
    const json = await exportBackup();
    Clipboard.setString(json);
    Alert.alert('Backup gerado', 'JSON copiado para a área de transferência. Cole num arquivo seguro.');
  }

  async function doImport() {
    try {
      const json = await Clipboard.getString();
      const res = await importBackup(json);
      Alert.alert(res.ok ? 'Importado' : 'Erro', res.ok ? 'Backup restaurado com validação de schema.' : res.error ?? 'Erro desconhecido');
    } catch {
      Alert.alert('Erro', 'Não consegui ler a área de transferência.');
    }
  }

  const protection = (
    <>
      {isNativeScreeningAvailable() && (
        <MetroSection>
          <MetroRow label="Papel de filtragem de chamadas" value={roleHeld ? 'Ativo' : 'Inativo'} color={roleHeld ? theme.primary : theme.danger} />
          <View style={{ flexDirection: 'row', gap: spacing.s }}>
            <MetroButton label="Conceder papel" variant="primary" size="small" onPress={async () => { await requestCallScreeningRole(); await refreshRole(); }} />
            <MetroButton label="Abrir configurações" size="small" onPress={() => openCallScreeningSettings()} />
          </View>
        </MetroSection>
      )}
      <MetroSection title="Proteção">
        <Flush>
          <View accessibilityRole="radiogroup">
            {MODES.map((m) => (
              <MetroRadio key={m.id} label={m.label} hint={m.desc} selected={mode === m.id} onSelect={() => setMode(m.id)} />
            ))}
          </View>
        </Flush>
      </MetroSection>
      {mode === 'CUSTOM' && (
        <MetroSection title="Personalizado">
          <MetroText variant="caption">Bloquear a partir do score:</MetroText>
          <View style={{ flexDirection: 'row', gap: spacing.s, flexWrap: 'wrap' }}>
            {[60, 70, 75, 85, 90].map((v) => (
              <MetroButton key={v} label={String(v)} size="small" variant={config.blockThreshold === v ? 'primary' : 'secondary'} onPress={() => setCustomConfig({ blockThreshold: v })} />
            ))}
          </View>
          <MetroText variant="caption" style={{ marginTop: spacing.s }}>Chamadas 0303 (telemarketing):</MetroText>
          <View style={{ flexDirection: 'row', gap: spacing.s, flexWrap: 'wrap' }}>
            {(Object.keys(HANDLE_0303_LABELS) as Handle0303[]).map((k) => (
              <MetroButton key={k} label={HANDLE_0303_LABELS[k]} size="small" variant={config.handle0303 === k ? 'primary' : 'secondary'} onPress={() => setCustomConfig({ handle0303: k })} />
            ))}
          </View>
        </MetroSection>
      )}
    </>
  );

  const rulesPage = (
    <MetroSection title={`Regras (${rules.length})`}>
      <Flush>
        {rules.map((r) => (
          <MetroListItem
            key={r.id}
            title={r.name}
            subtitle={`${r.match.kind}: ${('value' in r.match && r.match.value) || ('pattern' in r.match && r.match.pattern) || ''} · ação ${r.action}`}
            meta={r.enabled ? 'ativa' : 'desativada'}
            metaTone={r.enabled ? 'accent' : 'muted'}
            actions={[
              { label: r.enabled ? 'Desativar' : 'Ativar', accessibilityLabel: `${r.enabled ? 'Desativar' : 'Ativar'} regra ${r.name}`, onPress: () => upsertRule({ ...r, enabled: !r.enabled }) },
              { label: 'Remover', accessibilityLabel: `Remover regra ${r.name}`, onPress: () => removeRule(r.id) },
            ]}
          />
        ))}
      </Flush>
      <View style={{ flexDirection: 'row', gap: spacing.s, marginTop: spacing.s }}>
        <MetroTextField containerStyle={{ flex: 1 }} placeholder="Nome da regra" value={newRule.name} onChangeText={(v) => setNewRule((p) => ({ ...p, name: v }))} />
        <MetroTextField containerStyle={{ flex: 1 }} placeholder="+55114002…" value={newRule.prefix} onChangeText={(v) => setNewRule((p) => ({ ...p, prefix: v }))} keyboardType="phone-pad" />
      </View>
      <MetroButton block label="Adicionar regra de prefixo (bloquear)" icon={<FluentIcon name="add" />} variant="primary" onPress={addPrefixRule} />
    </MetroSection>
  );

  const listsPage = (
    <>
      <MetroSection title={`Permitidos (${allowlist.length})`}>
        <View>
          {allowlist.map((v) => (
            <MetroRow key={v} label={v} value="remover" color={theme.danger} />
          ))}
        </View>
        <View style={{ flexDirection: 'row', gap: spacing.s, alignItems: 'flex-start' }}>
          <MetroTextField containerStyle={{ flex: 1 }} placeholder="+5511999999999" value={listInput.allow} onChangeText={(v) => setListInput((p) => ({ ...p, allow: v }))} keyboardType="phone-pad" />
          <MetroButton label="Adicionar" size="small" onPress={() => addToList('allow')} />
        </View>
      </MetroSection>
      <MetroSection title={`Bloqueados (${blacklist.length})`}>
        <View>
          {blacklist.map((v) => (
            <MetroRow key={v} label={v} value="remover" color={theme.danger} />
          ))}
        </View>
        <View style={{ flexDirection: 'row', gap: spacing.s, alignItems: 'flex-start' }}>
          <MetroTextField containerStyle={{ flex: 1 }} placeholder="+5511999999999" value={listInput.block} onChangeText={(v) => setListInput((p) => ({ ...p, block: v }))} keyboardType="phone-pad" />
          <MetroButton label="Adicionar" size="small" onPress={() => addToList('block')} />
        </View>
      </MetroSection>
    </>
  );

  const generalPage = (
    <>
      <MetroSection title="Conta Conexus (opcional)">
        <Flush>
        <MetroListItem
          icon={<FluentIcon name="lock" />}
          title="Conta"
          subtitle={
            account.isSignedIn
              ? account.user?.email ?? account.user?.name ?? 'Conectado'
              : account.status === 'disabled'
                ? 'Em breve · o app funciona sem conta'
                : 'Backup na nuvem, denúncias identificadas e doações'
          }
          onPress={() => router.push('/account')}
          accessibilityHint="Abre a tela da Conta Conexus"
        />
        </Flush>
      </MetroSection>

      <MetroSection title="IA Assistente (OpenRouter)">
        <Flush>
          <MetroToggle
            label="IA Assistente"
            description="Off por padrão · nunca decide bloqueios"
            value={ai.enabled}
            onValueChange={(v) => setState({ ai: { ...ai, enabled: v } })}
          />
        </Flush>
        {ai.enabled && (
          <>
            <MetroTextField label="Chave OpenRouter" placeholder="sk-or-v1-…" value={aiKeyInput} onChangeText={setAiKeyInput} autoCapitalize="none" secureTextEntry />
            <MetroTextField label="Modelo" placeholder="Modelo (opcional, ex.: deepseek/deepseek-chat-v3.1:free)" value={ai.model} onChangeText={(v) => setState({ ai: { ...ai, model: v } })} autoCapitalize="none" />
            <MetroButton label="Salvar chave" variant="primary" size="small" onPress={() => { setState({ ai: { ...ai, apiKey: aiKeyInput.trim() } }); Alert.alert('Salvo', 'Chave armazenada apenas neste aparelho.'); }} />
            <MetroText variant="caption">
              Use modelos ":free" do OpenRouter. A chave sai do aparelho somente para openrouter.ai ao pedir análise.
            </MetroText>
          </>
        )}
      </MetroSection>

      <MetroSection title="Backup">
        <MetroText variant="body" tone="muted">
          Exporta modo, regras e listas em JSON validado. Import rejeita payloads maliciosos. Backup na nuvem: tela Conta.
        </MetroText>
        <View style={{ flexDirection: 'row', gap: spacing.s }}>
          <View style={{ flex: 1 }}>
            <MetroButton block label="Exportar (copiar)" size="small" onPress={doExport} />
          </View>
          <View style={{ flex: 1 }}>
            <MetroButton block label="Importar (colar)" size="small" onPress={doImport} />
          </View>
        </View>
      </MetroSection>

      <MetroSection title="Base local">
        <View>
          <MetroRow label="Registros de reputação" value={String(getDb().size)} />
          <MetroRow label="Chamadas no histórico" value={String(history.length)} />
          <MetroRow label="Armazenamento" value={account.isSignedIn ? 'aparelho + backup manual na conta' : 'somente no aparelho'} color={theme.primary} />
        </View>
      </MetroSection>

      <MetroSection title="Sobre">
        <View>
          <MetroRow label="Versão" value={APP_VERSION} />
          <MetroRow label="Licença" value="MIT (open source)" />
          <MetroRow label="Telemetria" value="nenhuma" color={theme.primary} />
          <MetroRow label="Anúncios" value="banner e intersticial ocasional (nunca durante chamadas)" />
        </View>
        {adPrivacyRequired && (
          <MetroButton block label="Privacidade de anúncios" icon={<FluentIcon name="lock" />} size="small" onPress={openAdPrivacy} />
        )}
        <MetroButton block label="Apoiar o projeto" icon={<FluentIcon name="heart" />} onPress={() => router.push('/donate')} />
      </MetroSection>

      <MetroSection title="Reset">
        <MetroButton label="Limpar histórico" variant="danger" size="small" onPress={() => setConfirmReset(true)} />
      </MetroSection>

      <MetroSection>
        <MetroText variant="caption">
          AntiSpam BR · fail-safe: em caso de dúvida, a chamada é permitida.
        </MetroText>
      </MetroSection>
    </>
  );

  const appearancePage = (
    <>
      <MetroSection title="Tema">
        <MetroRadioGroup
          accessibilityLabel="Tema"
          options={[
            { value: 'system', label: 'Usar do sistema' },
            { value: 'dark', label: 'Escuro' },
            { value: 'light', label: 'Claro' },
          ]}
          value={appearance.theme}
          onChange={(v) => setAppearance({ theme: v })}
        />
      </MetroSection>
      <MetroSection title="Cor de destaque">
        <View style={{ flexDirection: 'row', flexWrap: 'wrap' }}>
          {ACCENT_CHOICES.map((c) => (
            <AccentSwatch
              key={c.id}
              label={c.label}
              hex={c.hex}
              selected={appearance.accent === c.hex}
              onSelect={() => setAppearance({ accent: c.hex })}
            />
          ))}
        </View>
        <MetroTextField
          label="Cor personalizada"
          placeholder="#RRGGBB"
          value={customHex}
          onChangeText={(v) => {
            setCustomHex(v);
            setHexError(false);
          }}
          autoCapitalize="characters"
          autoCorrect={false}
          error={hexError ? 'Use o formato #RRGGBB, por exemplo #1BA1E2.' : undefined}
        />
        <MetroButton
          label="Aplicar cor"
          size="small"
          onPress={() => {
            const hex = normalizeHex(customHex);
            if (!hex) {
              setHexError(true);
              return;
            }
            setAppearance({ accent: hex });
            setCustomHex('');
          }}
        />
        <MetroButton label="Restaurar cor padrão" size="small" onPress={() => setAppearance({ accent: BRAND_PRIMARY })} />
      </MetroSection>
      <MetroSection title="Movimento">
        <MetroToggle
          label="Reduzir animações"
          description="Desliga o movimento das telas e dos controles."
          value={appearance.reduceMotion}
          onValueChange={(v) => setAppearance({ reduceMotion: v })}
        />
      </MetroSection>
    </>
  );

  return (
    <AppScreen scroll={false} title="Ajustes">
      <MetroPivotPager
        pages={[
          { key: 'protection', title: 'proteção', render: () => pivotPage(protection) },
          { key: 'rules', title: 'regras', render: () => pivotPage(rulesPage) },
          { key: 'lists', title: 'listas', render: () => pivotPage(listsPage) },
          { key: 'general', title: 'geral', render: () => pivotPage(generalPage) },
          { key: 'appearance', title: 'aparência', render: () => pivotPage(appearancePage) },
        ]}
      />
      <AdBanner />
      <MetroDialog
        visible={confirmReset}
        title="Confirmar"
        message="Apagar todo o histórico local?"
        onDismiss={() => setConfirmReset(false)}
        buttons={[
          { text: 'Apagar', style: 'destructive', onPress: () => setState({ history: [] }) },
          { text: 'Cancelar', style: 'cancel' },
        ]}
      />
    </AppScreen>
  );
}
