import { createSession, PENDING_KEY, TOKENS_KEY, type SessionDeps } from '../src/account/session';
import { createAntispamApi, createPaymentsApi, donationBody, parseReais, toCloudCategory } from '../src/account/api';
import { buildConfig } from '../src/account/config';
import { base64UrlEncode, utf8Encode } from '../src/account/base64';
import { AccountError, ACCOUNT_ERROR_MESSAGES } from '../src/account/errors';

function jwt(claims: object): string {
  const enc = (o: object) => base64UrlEncode(utf8Encode(JSON.stringify(o)));
  return `${enc({ alg: 'RS256' })}.${enc(claims)}.sig`;
}

type Call = { url: string; method: string; headers: Record<string, string>; body?: string };
type Reply = { status: number; body?: unknown };

function harness(opts: { clientId?: string; now?: number } = {}) {
  let clock = opts.now ?? 1_000_000;
  const store = new Map<string, string>();
  const calls: Call[] = [];
  const replies: Array<(c: Call) => Reply> = [];
  const fetchMock = jest.fn(async (url: string, init: { method: string; headers: Record<string, string>; body?: string }) => {
    const call = { url, method: init.method, headers: init.headers, body: init.body };
    calls.push(call);
    const next = replies.shift();
    if (!next) throw new Error('sem resposta configurada');
    const r = next(call);
    return { status: r.status, text: async () => (r.body === undefined ? '' : JSON.stringify(r.body)) };
  });
  const openAuthSession = jest.fn<ReturnType<SessionDeps['openAuthSession']>, Parameters<SessionDeps['openAuthSession']>>();
  const session = createSession({
    config: buildConfig({ clientId: opts.clientId ?? 'antispam-client' }),
    fetch: fetchMock,
    storage: {
      get: async (k) => store.get(k) ?? null,
      set: async (k, v) => {
        store.set(k, v);
      },
      remove: async (k) => {
        store.delete(k);
      },
    },
    now: () => clock,
    openAuthSession,
  });
  return {
    session,
    store,
    calls,
    fetchMock,
    openAuthSession,
    reply: (...r: Array<Reply | ((c: Call) => Reply)>) => replies.push(...r.map((x) => (typeof x === 'function' ? x : () => x))),
    advance: (ms: number) => {
      clock += ms;
    },
    signedIn(expiresInMs = 15 * 60_000, access = jwt({ sub: 'u1', email: 'u@x.com', name: 'U' })) {
      store.set(TOKENS_KEY, JSON.stringify({ accessToken: access, refreshToken: 'refresh-1', expiresAt: clock + expiresInMs }));
    },
  };
}

const tokenBody = (access: string, refresh = 'refresh-2') => ({ access_token: access, refresh_token: refresh, token_type: 'Bearer', expires_in: 900 });

describe('sessão sem client_id', () => {
  it('fica desligada e não chama a rede', async () => {
    const h = harness({ clientId: '' });
    await h.session.init();
    expect(h.session.getSnapshot().status).toBe('disabled');
    await expect(h.session.signIn()).rejects.toMatchObject({ code: 'disabled' });
    await expect(createAntispamApi(h.session).getBackup()).rejects.toMatchObject({ code: 'disabled' });
    expect(h.fetchMock).not.toHaveBeenCalled();
    expect(h.openAuthSession).not.toHaveBeenCalled();
  });
});

describe('login', () => {
  it('abre o navegador com PKCE, valida o state e troca o código', async () => {
    const h = harness();
    const access = jwt({ sub: 'u1', email: 'ana@x.com', name: 'Ana' });
    h.openAuthSession.mockImplementation(async (url) => {
      const u = new URL(url);
      expect(u.searchParams.get('code_challenge_method')).toBe('S256');
      expect(u.searchParams.get('redirect_uri')).toBe('antispambr://oauth/callback');
      return { type: 'success', url: `antispambr://oauth/callback?code=c1&state=${u.searchParams.get('state')}` };
    });
    h.reply({ status: 200, body: tokenBody(access) });
    const profile = await h.session.signIn();
    expect(profile).toEqual({ subject: 'u1', email: 'ana@x.com', name: 'Ana' });
    expect(h.session.getSnapshot().status).toBe('signedIn');
    const form = new URLSearchParams(h.calls[0].body);
    expect(h.calls[0].url).toBe('https://auth.conexus-systems.com.br/oauth/token');
    expect(form.get('grant_type')).toBe('authorization_code');
    expect(form.get('code')).toBe('c1');
    expect(form.get('code_verifier')).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(h.store.has(PENDING_KEY)).toBe(false);
    expect(JSON.parse(h.store.get(TOKENS_KEY)!).refreshToken).toBe('refresh-2');
  });

  it('rejeita retorno com state errado sem chamar /oauth/token', async () => {
    const h = harness();
    h.openAuthSession.mockResolvedValue({ type: 'success', url: 'antispambr://oauth/callback?code=c1&state=forjado' });
    await expect(h.session.signIn()).rejects.toMatchObject({ code: 'state_mismatch' });
    expect(h.fetchMock).not.toHaveBeenCalled();
    expect(h.store.has(TOKENS_KEY)).toBe(false);
  });

  it('cancelamento no navegador não grava nada', async () => {
    const h = harness();
    h.openAuthSession.mockResolvedValue({ type: 'cancel' });
    await expect(h.session.signIn()).rejects.toMatchObject({ code: 'cancelled' });
    expect(h.store.size).toBe(0);
  });

  it('invalid_client vira mensagem fixa (sem corpo do servidor)', async () => {
    const h = harness();
    h.openAuthSession.mockImplementation(async (url) => ({
      type: 'success',
      url: `antispambr://oauth/callback?code=c1&state=${new URL(url).searchParams.get('state')}`,
    }));
    h.reply({ status: 401, body: { error: 'invalid_client', error_description: 'segredo interno' } });
    const err = await h.session.signIn().catch((e) => e);
    expect(err).toBeInstanceOf(AccountError);
    expect(err.message).toBe(ACCOUNT_ERROR_MESSAGES.invalid_client);
    expect(err.message).not.toContain('segredo');
  });
});

describe('refresh', () => {
  it('renova 60 s antes de expirar e grava o refresh rotacionado', async () => {
    const h = harness();
    h.signedIn(59_000);
    const fresh = jwt({ sub: 'u1' });
    h.reply({ status: 200, body: tokenBody(fresh, 'refresh-3') }, { status: 200, body: { reports: [], page: 1, per_page: 50, total: 0 } });
    await createAntispamApi(h.session).listReports();
    expect(new URLSearchParams(h.calls[0].body).get('grant_type')).toBe('refresh_token');
    expect(new URLSearchParams(h.calls[0].body).get('refresh_token')).toBe('refresh-1');
    expect(h.calls[1].headers.Authorization).toBe(`Bearer ${fresh}`);
    expect(JSON.parse(h.store.get(TOKENS_KEY)!).refreshToken).toBe('refresh-3');
  });

  it('não renova com folga maior que 60 s', async () => {
    const h = harness();
    h.signedIn(120_000);
    h.reply({ status: 204 });
    await createAntispamApi(h.session).deleteMyData();
    expect(h.calls).toHaveLength(1);
    expect(h.calls[0].url).toBe('https://api.conexus-systems.com.br/api/v1/antispam/me');
    expect(h.calls[0].method).toBe('DELETE');
  });

  it('refresh recusado desconecta', async () => {
    const h = harness();
    await h.session.init();
    h.signedIn(10_000);
    h.reply({ status: 400, body: { error: 'invalid_grant' } });
    await expect(h.session.getAccessToken()).rejects.toMatchObject({ code: 'session_expired' });
    expect(h.store.has(TOKENS_KEY)).toBe(false);
    expect(h.session.getSnapshot().status).toBe('signedOut');
  });

  it('falha de rede no refresh mantém a sessão', async () => {
    const h = harness();
    h.signedIn(10_000);
    h.fetchMock.mockRejectedValueOnce(new Error('offline'));
    await expect(h.session.getAccessToken()).rejects.toMatchObject({ code: 'network' });
    expect(h.store.has(TOKENS_KEY)).toBe(true);
  });

  it('chamadas simultâneas fazem um único refresh', async () => {
    const h = harness();
    h.signedIn(1_000);
    const fresh = jwt({ sub: 'u1' });
    h.reply({ status: 200, body: tokenBody(fresh) });
    const [a, b] = await Promise.all([h.session.getAccessToken(), h.session.getAccessToken()]);
    expect(a).toBe(fresh);
    expect(b).toBe(fresh);
    expect(h.calls).toHaveLength(1);
  });
});

describe('cliente da API', () => {
  it('401 → refresh → nova tentativa com o token novo', async () => {
    const h = harness();
    h.signedIn();
    const fresh = jwt({ sub: 'u1' });
    h.reply(
      { status: 401, body: { error: 'unauthorized', message: 'x' } },
      { status: 200, body: tokenBody(fresh) },
      { status: 200, body: { version: 1, updated_at: '2026-10-01T10:00:00Z', device_name: 'Android', data: { blocked: [] } } },
    );
    const backup = await createAntispamApi(h.session).getBackup();
    expect(backup).toEqual({ version: 1, updatedAt: '2026-10-01T10:00:00Z', deviceName: 'Android', data: { blocked: [] } });
    expect(h.calls.map((c) => c.url)).toEqual([
      'https://api.conexus-systems.com.br/api/v1/antispam/backup',
      'https://auth.conexus-systems.com.br/oauth/token',
      'https://api.conexus-systems.com.br/api/v1/antispam/backup',
    ]);
    expect(h.calls[2].headers.Authorization).toBe(`Bearer ${fresh}`);
  });

  it('401 de novo depois do refresh desconecta', async () => {
    const h = harness();
    h.signedIn();
    h.reply({ status: 401 }, { status: 200, body: tokenBody(jwt({ sub: 'u1' })) }, { status: 401 });
    await expect(createAntispamApi(h.session).listReports()).rejects.toMatchObject({ code: 'session_expired' });
    expect(h.store.has(TOKENS_KEY)).toBe(false);
  });

  it('429 vira rate_limited com mensagem fixa', async () => {
    const h = harness();
    h.signedIn();
    h.reply({ status: 429, body: { error: 'rate_limited', message: 'limite diário: 50 (detalhe interno)' } });
    const err = await createAntispamApi(h.session)
      .createReport({ number: '+5511999999999', category: 'fraude' })
      .catch((e) => e);
    expect(err).toMatchObject({ code: 'rate_limited' });
    expect(err.message).toBe(ACCOUNT_ERROR_MESSAGES.rate_limited);
    expect(JSON.parse(h.calls[0].body!)).toEqual({ number: '+5511999999999', category: 'golpe' });
  });

  it('503 (JWKS fora) informa sem desconectar', async () => {
    const h = harness();
    h.signedIn();
    h.reply({ status: 503, body: { error: 'service_unavailable', message: 'jwks' } });
    const err = await createAntispamApi(h.session).getBackup().catch((e) => e);
    expect(err).toMatchObject({ code: 'service_unavailable', message: 'Serviço indisponível, tente em instantes.' });
    expect(h.store.has(TOKENS_KEY)).toBe(true);
  });

  it('403 forbidden da API do AntiSpam desconecta e pede login de novo', async () => {
    const h = harness();
    await h.session.init();
    h.signedIn();
    h.reply({ status: 403, body: { error: 'forbidden', message: 'aud' } });
    await expect(createAntispamApi(h.session).listReports()).rejects.toMatchObject({ code: 'reauth_required' });
    expect(h.store.has(TOKENS_KEY)).toBe(false);
    expect(h.session.getSnapshot().status).toBe('signedOut');
  });

  it('422 com details mostra só a mensagem fixa', async () => {
    const h = harness();
    h.signedIn();
    h.reply({ status: 422, body: { error: 'validation_failed', message: 'x', details: { number: ['inválido'] } } });
    const err = await createAntispamApi(h.session).createReport({ number: '+55 11 99999-0000', category: 'outro' }).catch((e) => e);
    expect(err.message).toBe(ACCOUNT_ERROR_MESSAGES.validation_failed);
    expect(JSON.parse(h.calls[0].body!).number).toBe('+5511999990000');
  });

  it('backup ausente (404) é null; 413 vira payload_too_large', async () => {
    const h = harness();
    h.signedIn();
    h.reply({ status: 404, body: { error: 'not_found' } }, { status: 413, body: { error: 'payload_too_large' } });
    const api = createAntispamApi(h.session);
    await expect(api.getBackup()).resolves.toBeNull();
    await expect(api.putBackup({ version: 1, data: { blocked: [], allowed: [], rules: [], settings: {} } })).rejects.toMatchObject({
      code: 'payload_too_large',
    });
    expect(h.calls[1].method).toBe('PUT');
    expect(h.calls[1].headers['Content-Type']).toContain('application/json');
  });

  it('não envia denúncia de número fora do E.164', async () => {
    const h = harness();
    h.signedIn();
    await expect(createAntispamApi(h.session).createReport({ number: '08001234567', category: 'spam' })).rejects.toMatchObject({
      code: 'number_not_supported',
    });
    expect(h.fetchMock).not.toHaveBeenCalled();
  });

  it('lista e exclui denúncias', async () => {
    const h = harness();
    h.signedIn();
    h.reply(
      {
        status: 200,
        body: {
          reports: [{ id: 7, number: '+5511988887777', category: 'robo', note: null, occurred_at: null, created_at: '2026-10-02T00:00:00.000Z' }],
          page: 2,
          per_page: 50,
          total: 51,
        },
      },
      { status: 204 },
    );
    const api = createAntispamApi(h.session);
    const page = await api.listReports(2);
    expect(h.calls[0].url).toBe('https://api.conexus-systems.com.br/api/v1/antispam/reports?page=2');
    expect(page).toEqual({
      reports: [{ id: '7', number: '+5511988887777', category: 'robo', note: null, occurredAt: null, createdAt: '2026-10-02T00:00:00.000Z' }],
      page: 2,
      perPage: 50,
      total: 51,
    });
    await api.deleteReport('7');
    expect(h.calls[1]).toMatchObject({ method: 'DELETE', url: 'https://api.conexus-systems.com.br/api/v1/antispam/reports/7' });
  });

  it('sair apaga os tokens e revoga o refresh token', async () => {
    const h = harness();
    h.signedIn();
    h.reply({ status: 200 });
    await h.session.signOut();
    expect(h.store.has(TOKENS_KEY)).toBe(false);
    expect(h.calls[0].url).toBe('https://auth.conexus-systems.com.br/oauth/revoke');
    expect(Object.fromEntries(new URLSearchParams(h.calls[0].body))).toEqual({ token: 'refresh-1', client_id: 'antispam-client' });
  });
});

describe('doações (Payments BFF)', () => {
  it('cria doação e só aceita checkout HTTPS', async () => {
    const h = harness();
    h.signedIn();
    h.reply(
      { status: 201, body: { donation_id: 12, checkout_url: 'https://pay.example.com/c/1', provider: 'crypto', provider_ref: 'x' } },
      { status: 201, body: { donation_id: 13, checkout_url: 'http://pay.example.com/c/2', provider: 'crypto', provider_ref: 'y' } },
    );
    const pay = createPaymentsApi(h.session);
    await expect(pay.donate(1000, ' obrigado ')).resolves.toEqual({ donationId: '12', checkoutUrl: 'https://pay.example.com/c/1', provider: 'crypto' });
    expect(h.calls[0].url).toBe('https://payments.conexus-systems.com.br/v1/me/donations');
    expect(JSON.parse(h.calls[0].body!)).toEqual({
      amount_cents: 1000,
      currency: 'BRL',
      product_slug: 'antispam-br',
      return_url: 'antispambr://billing/return',
      message: 'obrigado',
    });
    await expect(pay.donate(1000)).rejects.toMatchObject({ code: 'checkout_insecure' });
  });

  it('lista doações e mapeia audience_not_allowed', async () => {
    const h = harness();
    h.signedIn();
    h.reply(
      {
        status: 200,
        body: {
          donations: [
            { id: 1, amount_cents: 500, currency: 'BRL', status: 'paid', paid_at: null, product_slug: 'antispam-br', created_at: '2026-10-01T00:00:00Z' },
          ],
        },
      },
      { status: 403, body: { error: 'audience_not_allowed', message: 'x' } },
    );
    const pay = createPaymentsApi(h.session);
    await expect(pay.listDonations()).resolves.toEqual([
      { id: '1', amountCents: 500, currency: 'BRL', status: 'paid', productSlug: 'antispam-br', createdAt: '2026-10-01T00:00:00Z' },
    ]);
    await expect(pay.listDonations()).rejects.toMatchObject({ code: 'audience_not_allowed' });
  });

  it('limites de valor iguais ao servidor', () => {
    expect(() => donationBody(99)).toThrow(AccountError);
    expect(() => donationBody(1_000_001)).toThrow(AccountError);
    expect(parseReais('10,50')).toBe(1050);
    expect(parseReais('0,99')).toBeNull();
    expect(parseReais('abc')).toBeNull();
    expect(toCloudCategory('robocall')).toBe('robo');
  });
});
