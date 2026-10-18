import { challengeS256, newState, newVerifier } from '../src/account/pkce';
import {
  authorizeUrl,
  codeExchangeForm,
  needsRefresh,
  parseRedirect,
  parseTokens,
  profileFromJwt,
  refreshForm,
  PENDING_TTL_MS,
} from '../src/account/authContract';
import { base64UrlDecode, base64UrlEncode, utf8Decode, utf8Encode } from '../src/account/base64';
import { buildConfig, isAccountEnabled, REDIRECT_URI } from '../src/account/config';
import { AccountError } from '../src/account/errors';

function jwt(claims: object): string {
  const enc = (o: object) => base64UrlEncode(utf8Encode(JSON.stringify(o)));
  return `${enc({ alg: 'RS256', typ: 'JWT' })}.${enc(claims)}.assinatura`;
}

function codeOf(fn: () => unknown): string | undefined {
  try {
    fn();
  } catch (e) {
    return e instanceof AccountError ? e.code : 'other';
  }
  return undefined;
}

describe('PKCE S256', () => {
  it('reproduz o vetor do RFC 7636 (apêndice B)', async () => {
    await expect(challengeS256('dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk')).resolves.toBe(
      'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM',
    );
  });

  it('gera verifier de 43 caracteres base64url e state aleatório', () => {
    const v = newVerifier();
    expect(v).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(newVerifier()).not.toBe(v);
    expect(newState()).toMatch(/^[A-Za-z0-9_-]{32}$/);
  });

  it('recusa verifier fora do RFC', async () => {
    await expect(challengeS256('curto')).rejects.toThrow();
  });

  it('base64url ida e volta com UTF-8', () => {
    const bytes = utf8Encode('Ação ✓');
    expect(utf8Decode(base64UrlDecode(base64UrlEncode(bytes)))).toBe('Ação ✓');
  });
});

describe('config', () => {
  it('client_id vazio desliga o login e mantém URLs padrão HTTPS', () => {
    const c = buildConfig({ clientId: '' });
    expect(isAccountEnabled(c)).toBe(false);
    expect(c.authUrl).toBe('https://auth.conexus-systems.com.br');
    expect(c.apiUrl).toBe('https://api.conexus-systems.com.br');
    expect(c.paymentsUrl).toBe('https://payments.conexus-systems.com.br');
  });

  it('rejeita URL sem HTTPS e remove barra final', () => {
    const c = buildConfig({ clientId: 'abc', authUrl: 'http://evil.test', apiUrl: 'https://api.local.test/' });
    expect(isAccountEnabled(c)).toBe(true);
    expect(c.authUrl).toBe('https://auth.conexus-systems.com.br');
    expect(c.apiUrl).toBe('https://api.local.test');
  });
});

describe('URL de autorização e formulários', () => {
  it('monta /oauth/authorize com PKCE S256 e a redirect URI do app', () => {
    const url = new URL(authorizeUrl('https://auth.example.com', 'client-1', 'st ate', 'chal'));
    expect(url.origin + url.pathname).toBe('https://auth.example.com/oauth/authorize');
    expect(Object.fromEntries(url.searchParams)).toEqual({
      response_type: 'code',
      client_id: 'client-1',
      redirect_uri: 'antispambr://oauth/callback',
      state: 'st ate',
      code_challenge: 'chal',
      code_challenge_method: 'S256',
    });
  });

  it('troca de código e refresh sem segredo de cliente', () => {
    const exchange = new URLSearchParams(codeExchangeForm('client-1', 'c0de', 'verif'));
    expect(Object.fromEntries(exchange)).toEqual({
      grant_type: 'authorization_code',
      code: 'c0de',
      client_id: 'client-1',
      redirect_uri: REDIRECT_URI,
      code_verifier: 'verif',
    });
    const refresh = new URLSearchParams(refreshForm('client-1', 'r1'));
    expect(Object.fromEntries(refresh)).toEqual({ grant_type: 'refresh_token', refresh_token: 'r1', client_id: 'client-1' });
    expect(exchange.has('client_secret')).toBe(false);
  });
});

describe('parse do callback', () => {
  const now = 1_700_000_000_000;
  const pending = { state: 'abc123', verifier: 'v', at: now - 1000 };

  it('aceita a URI certa com o mesmo state', () => {
    expect(parseRedirect(`${REDIRECT_URI}?code=xyz&state=abc123`, pending, now)).toBe('xyz');
  });

  it('rejeita state errado', () => {
    expect(codeOf(() => parseRedirect(`${REDIRECT_URI}?code=xyz&state=outro`, pending, now))).toBe('state_mismatch');
  });

  it('rejeita sem login pendente ou depois de 10 minutos', () => {
    expect(codeOf(() => parseRedirect(`${REDIRECT_URI}?code=xyz&state=abc123`, null, now))).toBe('state_mismatch');
    const old = { ...pending, at: now - PENDING_TTL_MS - 1 };
    expect(codeOf(() => parseRedirect(`${REDIRECT_URI}?code=xyz&state=abc123`, old, now))).toBe('state_mismatch');
  });

  it('rejeita outra URI, erro do servidor e retorno sem código', () => {
    expect(codeOf(() => parseRedirect('evil://oauth/callback?code=x&state=abc123', pending, now))).toBe('invalid_redirect');
    expect(codeOf(() => parseRedirect(`${REDIRECT_URI}?error=access_denied&state=abc123`, pending, now))).toBe('login_denied');
    expect(codeOf(() => parseRedirect(`${REDIRECT_URI}?state=abc123`, pending, now))).toBe('invalid_redirect');
  });
});

describe('tokens e perfil', () => {
  const access = jwt({ sub: 'user-1', email: 'ana@example.com', name: 'Ana Souza' });

  it('valida a resposta do /oauth/token e calcula a expiração', () => {
    const t = parseTokens({ access_token: access, refresh_token: 'r', token_type: 'Bearer', expires_in: 900 }, 1000);
    expect(t).toEqual({ accessToken: access, refreshToken: 'r', expiresAt: 1000 + 900_000 });
    expect(codeOf(() => parseTokens({ access_token: 'x', refresh_token: 'r', token_type: 'Bearer', expires_in: 900 }, 0))).toBe('invalid_response');
    expect(codeOf(() => parseTokens({ access_token: access, refresh_token: 'r', token_type: 'mac', expires_in: 900 }, 0))).toBe('invalid_response');
  });

  it('renova 60 s antes de expirar', () => {
    const t = { accessToken: access, refreshToken: 'r', expiresAt: 100_000 };
    expect(needsRefresh(t, 100_000 - 61_000)).toBe(false);
    expect(needsRefresh(t, 100_000 - 60_000)).toBe(true);
  });

  it('lê nome e e-mail do JWT só para exibir', () => {
    expect(profileFromJwt(access)).toEqual({ subject: 'user-1', email: 'ana@example.com', name: 'Ana Souza' });
    expect(profileFromJwt('lixo')).toBeNull();
  });
});
