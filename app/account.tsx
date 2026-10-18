import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { Alert, View } from 'react-native';
import { useRouter } from 'expo-router';
import {
  GLYPHS,
  MetroButton,
  MetroDialog,
  MetroEmpty,
  MetroIconButton,
  MetroListItem,
  MetroLoading,
  MetroNotice,
  MetroSection,
  MetroText,
} from '@conexus/metro-ui';
import { useConexusAccount } from '@conexus/account-expo';
import { AppScreen, Flush, MetroRow } from '../src/ui/app';
import { getState } from '../src/app/store';
import {
  antispamApi,
  antispamErrorMessage,
  applyRestore,
  backupNow,
  CLOUD_REPORT_CATEGORY_LABELS,
  fetchRestorePreview,
  summaryText,
  type CloudReport,
} from '../src/account';

type Confirm = {
  title: string;
  message: string;
  action: string;
  destructive?: boolean;
  run: () => Promise<void>;
};

type BackupInfo = { state: 'loading' } | { state: 'none' } | { state: 'error'; message: string } | { state: 'ok'; updatedAt: string; deviceName: string | null };

function when(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString('pt-BR');
}

export default function Account() {
  const router = useRouter();
  const account = useConexusAccount();
  const { status, user } = account;
  const [busy, setBusy] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<Confirm | null>(null);
  const [backupInfo, setBackupInfo] = useState<BackupInfo>({ state: 'loading' });
  const [reports, setReports] = useState<CloudReport[]>([]);
  const [reportsMeta, setReportsMeta] = useState<{ page: number; total: number; loading: boolean; error: string | null }>({
    page: 0,
    total: 0,
    loading: false,
    error: null,
  });

  const loadBackupInfo = useCallback(async () => {
    setBackupInfo({ state: 'loading' });
    try {
      const b = await antispamApi.getBackup();
      setBackupInfo(b ? { state: 'ok', updatedAt: b.updatedAt, deviceName: b.deviceName } : { state: 'none' });
    } catch (e) {
      setBackupInfo({ state: 'error', message: antispamErrorMessage(e) });
    }
  }, []);

  const loadReports = useCallback(async (page: number) => {
    setReportsMeta((m) => ({ ...m, loading: true, error: null }));
    try {
      const res = await antispamApi.listReports(page);
      setReports((prev) => (page === 1 ? res.reports : [...prev, ...res.reports.filter((r) => !prev.some((p) => p.id === r.id))]));
      setReportsMeta({ page: res.page, total: res.total, loading: false, error: null });
    } catch (e) {
      setReportsMeta((m) => ({ ...m, loading: false, error: antispamErrorMessage(e) }));
    }
  }, []);

  useEffect(() => {
    if (status !== 'signedIn') return;
    void loadBackupInfo();
    void loadReports(1);
  }, [status, loadBackupInfo, loadReports]);

  async function run(key: string, fn: () => Promise<void>) {
    setBusy(key);
    try {
      await fn();
    } catch (e) {
      Alert.alert('Conta Conexus', antispamErrorMessage(e));
    } finally {
      setBusy(null);
    }
  }

  /** Tela padrão de login Conexus (só "Entrar" e "Criar conta", no navegador). */
  const signIn = () => router.push('/login');

  const signOut = () =>
    run('signout', async () => {
      await account.signOut();
      setReports([]);
      setBackupInfo({ state: 'loading' });
    });

  function askBackup() {
    const s = getState();
    const summary = summaryText({ blocked: s.blacklist.length, allowed: s.allowlist.length, rules: s.rules.length, mode: s.mode });
    setConfirm({
      title: 'Fazer backup agora',
      message: `Enviar ${summary}, além do modo de proteção e dos ajustes, para a sua Conta Conexus? O backup anterior na nuvem será substituído. Histórico de chamadas e a chave da IA não são enviados.`,
      action: 'Enviar',
      run: async () => {
        const res = await backupNow();
        setBackupInfo({ state: 'ok', updatedAt: res.updatedAt, deviceName: null });
        void loadBackupInfo();
        Alert.alert('Backup feito', `${summaryText(res.summary)} salvos na nuvem.`);
      },
    });
  }

  const askRestore = () =>
    run('restore', async () => {
      const preview = await fetchRestorePreview();
      if (!preview) {
        setBackupInfo({ state: 'none' });
        Alert.alert('Restaurar', 'Não há backup na nuvem para esta conta.');
        return;
      }
      setConfirm({
        title: 'Restaurar deste backup',
        message: `Backup de ${when(preview.updatedAt)}${preview.deviceName ? ` (${preview.deviceName})` : ''} com ${summaryText(preview.summary)}. Suas listas, regras e ajustes atuais neste aparelho serão substituídos.`,
        action: 'Restaurar',
        destructive: true,
        run: async () => {
          applyRestore(preview.patch);
          Alert.alert('Restaurado', `${summaryText(preview.summary)} aplicados neste aparelho.`);
        },
      });
    });

  function askDeleteBackup() {
    setConfirm({
      title: 'Apagar backup da nuvem',
      message: 'O backup guardado na sua Conta Conexus será apagado. As listas neste aparelho não mudam.',
      action: 'Apagar',
      destructive: true,
      run: async () => {
        await antispamApi.deleteBackup();
        setBackupInfo({ state: 'none' });
      },
    });
  }

  function askDeleteReport(r: CloudReport) {
    setConfirm({
      title: 'Excluir denúncia',
      message: `Excluir a denúncia de ${r.number} enviada em ${when(r.createdAt)}? A denúncia local no aparelho continua.`,
      action: 'Excluir',
      destructive: true,
      run: async () => {
        await antispamApi.deleteReport(r.id);
        setReports((prev) => prev.filter((p) => p.id !== r.id));
        setReportsMeta((m) => ({ ...m, total: Math.max(0, m.total - 1) }));
      },
    });
  }

  function askDeleteMyData() {
    setConfirm({
      title: 'Excluir meus dados na nuvem',
      message:
        'Apaga do servidor o seu backup e todas as denúncias enviadas com a sua conta. Não dá para desfazer. Os dados neste aparelho e a sua Conta Conexus continuam.',
      action: 'Excluir',
      destructive: true,
      run: async () => {
        await antispamApi.deleteMyData();
        setReports([]);
        setReportsMeta({ page: 1, total: 0, loading: false, error: null });
        setBackupInfo({ state: 'none' });
        Alert.alert('Dados excluídos', 'Backup e denúncias da sua conta foram apagados da nuvem.');
      },
    });
  }

  async function confirmNow() {
    const c = confirm;
    setConfirm(null);
    if (c) await run('confirm', c.run);
  }

  const whatGoes = (
    <MetroSection title="O que vai para a nuvem">
      <MetroText variant="body" tone="muted">
        Só o que você escolher: o backup (listas, regras e ajustes) quando tocar em "Fazer backup agora" e as denúncias em que marcar
        "Enviar com minha conta". Histórico de chamadas, contatos e a chave da IA nunca saem do aparelho. Sem conta, o app funciona igual.
      </MetroText>
    </MetroSection>
  );

  let body: ReactNode;
  if (status === 'disabled') {
    body = (
      <>
        <MetroSection>
          <MetroNotice title="Em breve" tone="info">
            O login com a Conta Conexus ainda não está ativo nesta versão. O AntiSpam BR continua 100% funcional, offline e sem cadastro.
          </MetroNotice>
        </MetroSection>
        <MetroSection title="Para que serve a conta">
          <MetroText variant="body" tone="muted">
            Quando ativada, a conta (opcional) permite guardar um backup das suas listas, enviar denúncias identificadas e acompanhar
            doações. Nada é desbloqueado por ela.
          </MetroText>
        </MetroSection>
      </>
    );
  } else if (status === 'loading') {
    body = <MetroLoading label="Lendo sessão" />;
  } else if (status === 'signedOut') {
    body = (
      <>
        <MetroSection title="Conta Conexus">
          <MetroText variant="body">
            Entre ou crie a sua Conta Conexus para fazer backup das listas, enviar denúncias identificadas e doar pela conta.
          </MetroText>
          <MetroButton block label="Entrar ou criar conta" icon={GLYPHS.lock} variant="primary" onPress={signIn} />
          <MetroText variant="caption">
            A mesma conta de todos os apps Conexus. O login acontece na página segura da Conta Conexus, no navegador: sua senha nunca passa pelo app.
          </MetroText>
        </MetroSection>
        {whatGoes}
      </>
    );
  } else {
    body = (
      <>
        <MetroSection title="Conta Conexus">
          <Flush>
            <MetroListItem icon={GLYPHS.lock} large title={user?.name || 'Conta Conexus'} subtitle={user?.email ?? undefined} />
          </Flush>
          <MetroButton label="Sair" size="small" loading={busy === 'signout'} onPress={signOut} />
        </MetroSection>

        <MetroSection title="Backup na nuvem">
          {backupInfo.state === 'loading' && <Flush><MetroLoading label="Consultando backup" /></Flush>}
          {backupInfo.state === 'none' && <MetroText variant="body" tone="muted">Nenhum backup na nuvem ainda.</MetroText>}
          {backupInfo.state === 'error' && <MetroText variant="body" tone="danger">{backupInfo.message}</MetroText>}
          {backupInfo.state === 'ok' && (
            <View>
              <MetroRow label="Último backup" value={when(backupInfo.updatedAt)} />
              {!!backupInfo.deviceName && <MetroRow label="Aparelho" value={backupInfo.deviceName} />}
            </View>
          )}
          <MetroButton block label="Fazer backup agora" icon={GLYPHS.copy} variant="primary" onPress={askBackup} />
          <MetroButton block label="Restaurar deste backup" icon={GLYPHS.history} loading={busy === 'restore'} onPress={askRestore} disabled={backupInfo.state === 'none'} />
          {backupInfo.state === 'ok' && <MetroButton label="Apagar backup da nuvem" variant="text" onPress={askDeleteBackup} />}
        </MetroSection>

        <MetroSection title={`Minhas denúncias${reportsMeta.total ? ` (${reportsMeta.total})` : ''}`}>
          {reportsMeta.error && <MetroText variant="body" tone="danger">{reportsMeta.error}</MetroText>}
          {!reportsMeta.error && !reportsMeta.loading && reports.length === 0 && (
            <Flush>
              <MetroEmpty message={'Nenhuma denúncia enviada com a conta.\nNo Histórico, ao denunciar, marque "Enviar com minha conta".'} />
            </Flush>
          )}
          <Flush>
            {reports.map((r) => (
              <MetroListItem
                key={r.id}
                title={r.number}
                subtitle={`${CLOUD_REPORT_CATEGORY_LABELS[r.category]} · ${when(r.createdAt)}${r.note ? ` · ${r.note}` : ''}`}
                trailing={
                  <MetroIconButton icon={GLYPHS.delete} accessibilityLabel={`Excluir denúncia de ${r.number}`} onPress={() => askDeleteReport(r)} />
                }
              />
            ))}
          </Flush>
          {reportsMeta.loading && <Flush><MetroLoading label="Carregando denúncias" /></Flush>}
          {!reportsMeta.loading && reports.length < reportsMeta.total && (
            <MetroButton label="Carregar mais" size="small" onPress={() => loadReports(reportsMeta.page + 1)} />
          )}
          {!reportsMeta.loading && reportsMeta.error && (
            <MetroButton label="Tentar de novo" size="small" onPress={() => loadReports(1)} />
          )}
        </MetroSection>

        <MetroSection title="Doações">
          <MetroButton block label="Doar e ver minhas doações" icon={GLYPHS.heart} onPress={() => router.push('/donate')} />
        </MetroSection>

        {whatGoes}

        <MetroSection title="Seus dados">
          <MetroButton block label="Excluir meus dados na nuvem" icon={GLYPHS.delete} variant="danger" onPress={askDeleteMyData} />
          <MetroText variant="caption">Apaga backup e denúncias desta conta no servidor do AntiSpam BR.</MetroText>
          <MetroButton label="Excluir a Conta Conexus" variant="text" accessibilityRole="link" onPress={() => void account.openDeleteAccount()} />
          <MetroText variant="caption">{account.deleteAccountUrl}</MetroText>
        </MetroSection>
      </>
    );
  }

  return (
    <AppScreen title="Conta">
      {body}
      {busy === 'confirm' && <MetroLoading label="Enviando" />}
      <MetroSection>
        <MetroText variant="caption">Conta opcional · o app funciona inteiro sem ela.</MetroText>
      </MetroSection>
      <MetroDialog
        visible={!!confirm}
        title={confirm?.title ?? ''}
        message={confirm?.message}
        onDismiss={() => setConfirm(null)}
        buttons={
          confirm
            ? [
                { text: confirm.action, style: confirm.destructive ? 'destructive' : 'default', onPress: () => void confirmNow() },
                { text: 'Cancelar', style: 'cancel' },
              ]
            : []
        }
      />
    </AppScreen>
  );
}
