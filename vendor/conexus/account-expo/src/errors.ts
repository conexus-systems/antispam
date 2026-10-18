/**
 * Erros da Conta Conexus com mensagens FIXAS em pt-BR por código. Corpo de resposta, tokens e detalhes do
 * servidor nunca chegam à tela nem ao log.
 */

export type ConexusAccountErrorCode =
  | "disabled"
  | "not_signed_in"
  | "session_expired"
  | "cancelled"
  | "state_mismatch"
  | "invalid_redirect"
  | "login_denied"
  | "invalid_client"
  | "invalid_grant"
  | "invalid_response"
  | "network"
  | "unauthorized"
  | "forbidden"
  | "account_inactive"
  | "identity_conflict"
  | "audience_not_allowed"
  | "not_found"
  | "validation_failed"
  | "payload_too_large"
  | "rate_limited"
  | "service_unavailable"
  | "not_configured"
  | "insecure_url"
  | "unavailable";

export const CONEXUS_ACCOUNT_ERROR_MESSAGES: Record<ConexusAccountErrorCode, string> = {
  disabled: "A Conta Conexus ainda não está disponível nesta versão do app.",
  not_signed_in: "Entre com a Conta Conexus para continuar.",
  session_expired: "Sessão expirada. Entre novamente.",
  cancelled: "Login cancelado.",
  state_mismatch: "Sessão de login expirada ou inválida. Tente novamente.",
  invalid_redirect: "Retorno de login inválido.",
  login_denied: "O login não foi autorizado.",
  invalid_client: "Este app ainda não está registrado na Conta Conexus.",
  invalid_grant: "Login expirado. Tente novamente.",
  invalid_response: "Resposta inesperada do servidor.",
  network: "Sem conexão com os serviços Conexus. Verifique a internet e tente novamente.",
  unauthorized: "Sessão expirada. Entre novamente.",
  forbidden: "Acesso negado pelo servidor.",
  account_inactive: "Sua Conta Conexus está desativada ou foi excluída.",
  identity_conflict: "Este e-mail já tem uma conta neste app. Confirme o e-mail na Conta Conexus e entre de novo.",
  audience_not_allowed: "Este app ainda não está autorizado neste serviço Conexus.",
  not_found: "Nada encontrado.",
  validation_failed: "Dados não aceitos pelo servidor.",
  payload_too_large: "Dados grandes demais para enviar.",
  rate_limited: "Limite de uso atingido. Tente novamente mais tarde.",
  service_unavailable: "Serviço indisponível, tente em instantes.",
  not_configured: "A API deste app não está configurada.",
  insecure_url: "Endereço recusado (não é HTTPS).",
  unavailable: "Serviço Conexus indisponível no momento.",
};

export class ConexusAccountError extends Error {
  readonly code: ConexusAccountErrorCode;
  constructor(code: ConexusAccountErrorCode) {
    super(CONEXUS_ACCOUNT_ERROR_MESSAGES[code]);
    this.name = "ConexusAccountError";
    this.code = code;
  }
}

export function isConexusAccountError(error: unknown, code?: ConexusAccountErrorCode): error is ConexusAccountError {
  return error instanceof ConexusAccountError && (code === undefined || error.code === code);
}

/** Mensagem segura para qualquer erro (nunca expõe detalhes de exceções desconhecidas). */
export function accountErrorMessage(error: unknown): string {
  if (error instanceof ConexusAccountError) return error.message;
  return CONEXUS_ACCOUNT_ERROR_MESSAGES.unavailable;
}

/** Mapeia status HTTP + código `error` do corpo (contrato `ConexusBearerAuth`) para um código fixo. */
export function errorFromResponse(status: number, errorCode: string | null): ConexusAccountError {
  switch (errorCode) {
    case "invalid_client":
    case "invalid_grant":
    case "audience_not_allowed":
    case "validation_failed":
    case "payload_too_large":
    case "rate_limited":
    case "not_found":
    case "forbidden":
    case "account_inactive":
    case "identity_conflict":
      return new ConexusAccountError(errorCode);
    case "service_unavailable":
    case "auth_unavailable":
      return new ConexusAccountError("service_unavailable");
    case "invalid_token":
    case "missing_token":
    case "unauthorized":
      return new ConexusAccountError("unauthorized");
    default:
      break;
  }
  if (status === 401) return new ConexusAccountError("unauthorized");
  if (status === 403) return new ConexusAccountError("forbidden");
  if (status === 404) return new ConexusAccountError("not_found");
  if (status === 413) return new ConexusAccountError("payload_too_large");
  if (status === 422 || status === 400) return new ConexusAccountError("validation_failed");
  if (status === 429) return new ConexusAccountError("rate_limited");
  if (status === 503) return new ConexusAccountError("service_unavailable");
  return new ConexusAccountError("unavailable");
}
