/**
 * Erros da Conta Conexus com mensagens FIXAS por código. Corpo de resposta, tokens e detalhes do
 * servidor nunca chegam à tela nem ao log.
 */

export type AccountErrorCode =
  | 'disabled'
  | 'not_signed_in'
  | 'session_expired'
  | 'cancelled'
  | 'state_mismatch'
  | 'invalid_redirect'
  | 'login_denied'
  | 'invalid_client'
  | 'invalid_grant'
  | 'invalid_response'
  | 'network'
  | 'unauthorized'
  | 'forbidden'
  | 'audience_not_allowed'
  | 'not_found'
  | 'validation_failed'
  | 'payload_too_large'
  | 'rate_limited'
  | 'checkout_insecure'
  | 'number_not_supported'
  | 'backup_invalid'
  | 'service_unavailable'
  | 'reauth_required'
  | 'unavailable';

export const ACCOUNT_ERROR_MESSAGES: Record<AccountErrorCode, string> = {
  disabled: 'A Conta Conexus ainda não está disponível nesta versão do app.',
  not_signed_in: 'Entre com a Conta Conexus para continuar.',
  session_expired: 'Sessão expirada. Entre novamente.',
  cancelled: 'Login cancelado.',
  state_mismatch: 'Sessão de login expirada ou inválida. Tente novamente.',
  invalid_redirect: 'Retorno de login inválido.',
  login_denied: 'O login não foi autorizado.',
  invalid_client: 'O AntiSpam BR ainda não está registrado na Conta Conexus.',
  invalid_grant: 'Login expirado. Tente novamente.',
  invalid_response: 'Resposta inesperada do servidor.',
  network: 'Sem conexão com os serviços Conexus. O app continua funcionando sem conta.',
  unauthorized: 'Sessão expirada. Entre novamente.',
  forbidden: 'Acesso negado pelo servidor.',
  audience_not_allowed: 'O AntiSpam BR ainda não está autorizado neste serviço Conexus.',
  not_found: 'Nada encontrado na nuvem.',
  validation_failed: 'Dados não aceitos pelo servidor.',
  payload_too_large: 'Dados grandes demais para enviar (backup: limite de 256 KB).',
  rate_limited: 'Limite de uso atingido. Tente novamente mais tarde (denúncias: até 50 por dia).',
  checkout_insecure: 'Link de pagamento recusado (não é HTTPS).',
  number_not_supported: 'Este número não está no formato internacional (+55…) e não pode ser enviado.',
  backup_invalid: 'O backup da nuvem está em formato inválido e não foi aplicado.',
  service_unavailable: 'Serviço indisponível, tente em instantes.',
  reauth_required: 'Esta sessão não vale para o AntiSpam BR. Entre novamente.',
  unavailable: 'Serviço Conexus indisponível no momento. O app continua funcionando sem conta.',
};

export class AccountError extends Error {
  readonly code: AccountErrorCode;
  constructor(code: AccountErrorCode) {
    super(ACCOUNT_ERROR_MESSAGES[code]);
    this.name = 'AccountError';
    this.code = code;
  }
}

/** Mensagem segura para qualquer erro (nunca expõe detalhes de exceções desconhecidas). */
export function accountErrorMessage(error: unknown): string {
  if (error instanceof AccountError) return error.message;
  return ACCOUNT_ERROR_MESSAGES.unavailable;
}

/** Mapeia status HTTP + código `error` do corpo para um código fixo. */
export function errorFromResponse(status: number, errorCode: string | null): AccountError {
  switch (errorCode) {
    case 'invalid_client':
    case 'invalid_grant':
    case 'audience_not_allowed':
    case 'validation_failed':
    case 'payload_too_large':
    case 'rate_limited':
    case 'not_found':
    case 'forbidden':
      return new AccountError(errorCode);
    case 'service_unavailable':
    case 'auth_unavailable':
      return new AccountError('service_unavailable');
    case 'invalid_token':
    case 'unauthorized':
      return new AccountError('unauthorized');
    default:
      break;
  }
  if (status === 401) return new AccountError('unauthorized');
  if (status === 403) return new AccountError('forbidden');
  if (status === 404) return new AccountError('not_found');
  if (status === 413) return new AccountError('payload_too_large');
  if (status === 422 || status === 400) return new AccountError('validation_failed');
  if (status === 429) return new AccountError('rate_limited');
  if (status === 503) return new AccountError('service_unavailable');
  return new AccountError('unavailable');
}
