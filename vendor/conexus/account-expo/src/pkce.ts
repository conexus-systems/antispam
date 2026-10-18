/**
 * PKCE (RFC 7636) só com S256 — o único método que o Auth BFF aceita. Aleatoriedade e SHA-256 vêm do
 * expo-crypto (gerador seguro do sistema).
 */
import * as Crypto from 'expo-crypto';
import { asciiBytes, base64UrlEncode } from './base64';

const VERIFIER = /^[A-Za-z0-9\-._~]{43,128}$/;

function randomToken(bytes: number): string {
  return base64UrlEncode(Crypto.getRandomBytes(bytes));
}

/** 32 bytes aleatórios = 43 caracteres base64url (mínimo do RFC). */
export function newVerifier(): string {
  return randomToken(32);
}

export function newState(): string {
  return randomToken(24);
}

export async function challengeS256(verifier: string): Promise<string> {
  if (!VERIFIER.test(verifier)) throw new Error('code_verifier inválido');
  const digest = await Crypto.digest(Crypto.CryptoDigestAlgorithm.SHA256, asciiBytes(verifier));
  return base64UrlEncode(new Uint8Array(digest));
}
