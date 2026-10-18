import { useState } from 'react';
import { KeyboardAvoidingView, Platform, View } from 'react-native';
import { spacing, useTheme } from '../../src/ui/theme';
import {
  MetroButton,
  MetroHeader,
  MetroPage,
  MetroRow,
  MetroScore,
  MetroSection,
  MetroTag,
  MetroText,
  MetroTextField,
} from '../../src/ui/metro';
import { useAppState, toggleList } from '../../src/app/store';
import { getDb, ensureDb } from '../../src/app/screening';
import { normalizePhone, formatDisplay } from '../../src/core/phone/normalize';
import { computeSpamScore, riskBand } from '../../src/core/detection/spamScore';
import { analyzeNumber, type AiInsight } from '../../src/core/ai/openrouter';

export default function Verify() {
  const theme = useTheme();
  const ai = useAppState((s) => s.ai);
  const [number, setNumber] = useState('');
  const [analyzing, setAnalyzing] = useState(false);
  const [result, setResult] = useState<null | {
    display: string;
    canonical: string;
    kind: string;
    ddd?: string;
    score: number;
    confidence: string;
    band: string;
    contributions: Array<{ label: string; points: number }>;
    repReports: number;
  }>(null);
  const [aiInsight, setAiInsight] = useState<AiInsight | null>(null);
  const [aiError, setAiError] = useState<string | null>(null);
  const [aiLoading, setAiLoading] = useState(false);

  async function analyze() {
    if (!number.trim()) return;
    setAnalyzing(true);
    setAiInsight(null);
    setAiError(null);
    try {
      await ensureDb();
      const n = normalizePhone(number.trim());
      const rep = getDb().get(n.canonical);
      const breakdown = computeSpamScore({
        phone: n,
        reputation: rep ? { score: rep.score, reports: rep.reports, confidence: rep.confidence } : null,
      });
      setResult({
        display: formatDisplay(n.canonical),
        canonical: n.canonical || '—',
        kind: n.kind,
        ddd: n.ddd,
        score: breakdown.score,
        confidence: breakdown.confidence,
        band: riskBand(breakdown.score),
        contributions: breakdown.contributions,
        repReports: rep?.reports ?? 0,
      });
    } finally {
      setAnalyzing(false);
    }
  }

  async function askAi() {
    if (!result) return;
    setAiLoading(true);
    setAiError(null);
    try {
      const insight = await analyzeNumber(
        { apiKey: ai.apiKey, model: ai.model || undefined },
        {
          number: result.canonical,
          kind: result.kind,
          ddd: result.ddd,
          localScore: result.score,
          localReasons: result.contributions.map((c) => c.label),
        },
      );
      setAiInsight(insight);
    } catch (e) {
      setAiError(e instanceof Error ? e.message : 'Falha na análise de IA');
    } finally {
      setAiLoading(false);
    }
  }

  return (
    <KeyboardAvoidingView style={{ flex: 1, backgroundColor: theme.bg }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <MetroPage header={<MetroHeader title="Verificar número" />}>
        <MetroSection>
          <MetroTextField
            label="Número"
            value={number}
            onChangeText={setNumber}
            placeholder="(11) 99999-9999 ou +5511…"
            keyboardType="phone-pad"
            onSubmitEditing={analyze}
            help="A análise local nunca envia o número para a Internet."
          />
          <MetroButton title="Analisar" variant="primary" onPress={analyze} loading={analyzing} />
        </MetroSection>

        {result && (
          <>
            <View style={{ flexDirection: 'row', gap: spacing.l, alignItems: 'flex-start' }}>
              <MetroScore score={result.score} />
              <View style={{ flex: 1, gap: spacing.xs }}>
                <MetroText variant="subtitle">{result.display}</MetroText>
                <MetroText variant="caption">Canônico: {result.canonical}</MetroText>
                <View style={{ flexDirection: 'row', gap: spacing.xs, flexWrap: 'wrap', marginTop: spacing.xs }}>
                  <MetroTag text={result.band} color={result.score >= 61 ? theme.danger : result.score >= 41 ? theme.warn : theme.primary} filled />
                  <MetroTag text={`tipo: ${result.kind}`} color={theme.info} />
                  {result.ddd && <MetroTag text={`DDD ${result.ddd}`} color={theme.info} />}
                </View>
              </View>
            </View>

            <MetroSection title="Por que este score?">
              {result.contributions.length === 0 && (
                <MetroText variant="body" tone="mute">Nenhum sinal de risco encontrado nesta base local.</MetroText>
              )}
              <View>
                {result.contributions.map((c, i) => (
                  <MetroRow key={i} label={c.label} value={`+${c.points}`} color={theme.warn} />
                ))}
                <MetroRow label="Confiança" value={result.confidence} />
                {result.repReports > 0 && <MetroRow label="Denúncias na sua base" value={String(result.repReports)} />}
              </View>
            </MetroSection>

            <MetroSection title="Ações rápidas">
              <View style={{ flexDirection: 'row', gap: spacing.s, flexWrap: 'wrap' }}>
                <MetroButton title="Sempre permitir" icon="check" small onPress={() => toggleList('allowlist', result.canonical)} />
                <MetroButton title="Bloquear sempre" icon="block" variant="danger" small onPress={() => toggleList('blacklist', result.canonical)} />
              </View>
            </MetroSection>

            <MetroSection title="Análise de IA (opcional)">
              <MetroTag text={ai.enabled ? 'ativada' : 'desativada'} color={ai.enabled ? theme.primary : theme.mute} />
              {!ai.enabled && (
                <MetroText variant="body" tone="muted">
                  Ative a IA Assistente em Ajustes para pedir uma segunda opinião a um modelo via OpenRouter
                  (você fornece a própria chave; ela fica só no aparelho). A IA NUNCA decide bloqueios.
                </MetroText>
              )}
              {ai.enabled && !aiInsight && (
                <MetroButton title="Pedir análise à IA" onPress={askAi} loading={aiLoading} />
              )}
              {aiError && <MetroText variant="body" tone="danger" accessibilityLiveRegion="polite">{aiError}</MetroText>}
              {aiInsight && (
                <View style={{ gap: spacing.s, marginTop: spacing.xs }}>
                  <MetroTag text={`IA: ${aiInsight.riskLevel}`} color={aiInsight.riskLevel === 'alto' ? theme.danger : aiInsight.riskLevel === 'médio' ? theme.warn : theme.primary} filled />
                  <MetroText variant="subtitle">{aiInsight.verdict}</MetroText>
                  {aiInsight.reasoning.map((r, i) => (
                    <MetroText key={`r${i}`} variant="body" tone="muted">• {r}</MetroText>
                  ))}
                  {aiInsight.advice.map((a, i) => (
                    <MetroText key={`a${i}`} variant="body" tone="info">→ {a}</MetroText>
                  ))}
                  <MetroText variant="caption">
                    Opinião gerada por IA — use como apoio, não como verdade.
                  </MetroText>
                </View>
              )}
            </MetroSection>
          </>
        )}
      </MetroPage>
    </KeyboardAvoidingView>
  );
}
