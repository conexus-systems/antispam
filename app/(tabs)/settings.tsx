import { useState } from 'react';
import { Alert, Clipboard, Pressable, View } from 'react-native';
import { useRouter } from 'expo-router';
import { spacing, useTheme } from '../../src/ui/theme';
import {
  MetroButton,
  MetroDialog,
  MetroHeader,
  MetroIconButton,
  MetroListItem,
  MetroPage,
  MetroPivot,
  MetroRadio,
  MetroRow,
  MetroSection,
  MetroSwitch,
  MetroText,
  MetroTextField,
  MetroToggle,
} from '../../src/ui/metro';
import {
  useAppState,
  setMode,
  setCustomConfig,
  upsertRule,
  removeRule,
  toggleList,
  getState,
  setState,
} from '../../src/app/store';
import { importBackup, exportBackup } from '../../src/app/backup';
import { getDb } from '../../src/app/screening';
import { isCallScreeningRoleHeld, isNativeScreeningAvailable, openCallScreeningSettings, requestCallScreeningRole } from '../../src/app/nativeBridge';
import { HANDLE_0303_LABELS, type Handle0303 } from '../../src/core/phone/brazilRules';
import type { ProtectionMode } from '../../src/core/types';

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
  const history = useAppState((s) => s.history);

  const [newRule, setNewRule] = useState({ name: '', prefix: '' });
  const [listInput, setListInput] = useState({ allow: '', block: '' });
  const [aiKeyInput, setAiKeyInput] = useState(ai.apiKey);
  const [roleHeld, setRoleHeld] = useState<boolean | null>(null);

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
            <MetroButton title="Conceder papel" variant="primary" small onPress={async () => { await requestCallScreeningRole(); await refreshRole(); }} />
            <MetroButton title="Abrir configurações" small onPress={() => openCallScreeningSettings()} />
          </View>
        </MetroSection>
      )}
      <MetroSection title="Proteção">
        <View accessibilityRole="radiogroup">
          {MODES.map((m) => (
            <MetroRadio key={m.id} label={m.label} description={m.desc} selected={mode === m.id} onSelect={() => setMode(m.id)} />
          ))}
        </View>
      </MetroSection>
      {mode === 'CUSTOM' && (
        <MetroSection title="Personalizado">
          <MetroText variant="caption">Bloquear a partir do score:</MetroText>
          <View style={{ flexDirection: 'row', gap: spacing.s, flexWrap: 'wrap' }}>
            {[60, 70, 75, 85, 90].map((v) => (
              <MetroButton key={v} title={String(v)} small variant={config.blockThreshold === v ? 'primary' : 'secondary'} onPress={() => setCustomConfig({ blockThreshold: v })} />
            ))}
          </View>
          <MetroText variant="caption" style={{ marginTop: spacing.s }}>Chamadas 0303 (telemarketing):</MetroText>
          <View style={{ flexDirection: 'row', gap: spacing.s, flexWrap: 'wrap' }}>
            {(Object.keys(HANDLE_0303_LABELS) as Handle0303[]).map((k) => (
              <MetroButton key={k} title={HANDLE_0303_LABELS[k]} small variant={config.handle0303 === k ? 'primary' : 'secondary'} onPress={() => setCustomConfig({ handle0303: k })} />
            ))}
          </View>
        </MetroSection>
      )}
    </>
  );

  const rulesPage = (
    <MetroSection title={`Regras (${rules.length})`}>
      <View>
        {rules.map((r) => (
          <MetroListItem
            key={r.id}
            title={r.name}
            subtitle={`${r.match.kind}: ${('value' in r.match && r.match.value) || ('pattern' in r.match && r.match.pattern) || ''} · ação ${r.action}`}
            divider
            right={
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.xs }}>
                <Pressable
                  onPress={() => upsertRule({ ...r, enabled: !r.enabled })}
                  accessibilityRole="switch"
                  accessibilityLabel={r.name}
                  accessibilityState={{ checked: r.enabled }}
                  hitSlop={12}
                  style={{ minHeight: 44, justifyContent: 'center' }}
                >
                  <MetroSwitch value={r.enabled} />
                </Pressable>
                <MetroIconButton icon="close" label={`Remover regra ${r.name}`} onPress={() => removeRule(r.id)} />
              </View>
            }
          />
        ))}
      </View>
      <View style={{ flexDirection: 'row', gap: spacing.s, marginTop: spacing.s }}>
        <MetroTextField containerStyle={{ flex: 1 }} placeholder="Nome da regra" value={newRule.name} onChangeText={(v) => setNewRule((p) => ({ ...p, name: v }))} />
        <MetroTextField containerStyle={{ flex: 1 }} placeholder="+55114002…" value={newRule.prefix} onChangeText={(v) => setNewRule((p) => ({ ...p, prefix: v }))} keyboardType="phone-pad" />
      </View>
      <MetroButton title="Adicionar regra de prefixo (bloquear)" icon="add" variant="primary" onPress={addPrefixRule} />
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
          <MetroButton title="Adicionar" small onPress={() => addToList('allow')} />
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
          <MetroButton title="Adicionar" small onPress={() => addToList('block')} />
        </View>
      </MetroSection>
    </>
  );

  const generalPage = (
    <>
      <MetroSection title="IA Assistente (OpenRouter)">
        <MetroToggle
          label="IA Assistente"
          description="Off por padrão · nunca decide bloqueios"
          value={ai.enabled}
          onValueChange={(v) => setState({ ai: { ...ai, enabled: v } })}
        />
        {ai.enabled && (
          <>
            <MetroTextField label="Chave OpenRouter" placeholder="sk-or-v1-…" value={aiKeyInput} onChangeText={setAiKeyInput} autoCapitalize="none" secureTextEntry />
            <MetroTextField label="Modelo" placeholder="Modelo (opcional, ex.: deepseek/deepseek-chat-v3.1:free)" value={ai.model} onChangeText={(v) => setState({ ai: { ...ai, model: v } })} autoCapitalize="none" />
            <MetroButton title="Salvar chave" variant="primary" small style={{ alignSelf: 'flex-start' }} onPress={() => { setState({ ai: { ...ai, apiKey: aiKeyInput.trim() } }); Alert.alert('Salvo', 'Chave armazenada apenas neste aparelho.'); }} />
            <MetroText variant="caption">
              Use modelos ":free" do OpenRouter. A chave sai do aparelho somente para openrouter.ai ao pedir análise.
            </MetroText>
          </>
        )}
      </MetroSection>

      <MetroSection title="Backup">
        <MetroText variant="body" tone="muted">
          Exporta modo, regras e listas em JSON validado. Import rejeita payloads maliciosos.
        </MetroText>
        <View style={{ flexDirection: 'row', gap: spacing.s }}>
          <View style={{ flex: 1 }}>
            <MetroButton title="Exportar (copiar)" small onPress={doExport} />
          </View>
          <View style={{ flex: 1 }}>
            <MetroButton title="Importar (colar)" small onPress={doImport} />
          </View>
        </View>
      </MetroSection>

      <MetroSection title="Base local">
        <View>
          <MetroRow label="Registros de reputação" value={String(getDb().size)} />
          <MetroRow label="Chamadas no histórico" value={String(history.length)} />
          <MetroRow label="Armazenamento" value="somente no aparelho" color={theme.primary} />
        </View>
      </MetroSection>

      <MetroSection title="Sobre">
        <View>
          <MetroRow label="Versão" value="0.2.0 (MVP)" />
          <MetroRow label="Licença" value="MIT (open source)" />
          <MetroRow label="Telemetria" value="nenhuma" color={theme.primary} />
          <MetroRow label="Anúncios" value="nenhum" color={theme.primary} />
        </View>
        <MetroButton title="Apoiar o projeto" icon="heart" onPress={() => router.push('/donate')} />
      </MetroSection>

      <MetroSection title="Reset">
        <MetroButton title="Limpar histórico" variant="danger" small style={{ alignSelf: 'flex-start' }} onPress={() => setConfirmReset(true)} />
      </MetroSection>

      <MetroText variant="caption">
        AntiSpam BR · fail-safe: em caso de dúvida, a chamada é permitida.
      </MetroText>
    </>
  );

  return (
    <MetroPage scroll={false} header={<MetroHeader title="Ajustes" />}>
      <MetroPivot
        pages={[
          { key: 'protection', title: 'proteção', render: () => protection },
          { key: 'rules', title: 'regras', render: () => rulesPage },
          { key: 'lists', title: 'listas', render: () => listsPage },
          { key: 'general', title: 'geral', render: () => generalPage },
        ]}
      />
      <MetroDialog
        visible={confirmReset}
        title="Confirmar"
        message="Apagar todo o histórico local?"
        onRequestClose={() => setConfirmReset(false)}
        buttons={[
          { title: 'Apagar', destructive: true, onPress: () => { setState({ history: [] }); setConfirmReset(false); } },
          { title: 'Cancelar', onPress: () => setConfirmReset(false) },
        ]}
      />
    </MetroPage>
  );
}
