import { useRouter } from 'expo-router';
import { ConexusLoginScreen } from '@conexus/account-expo';

/** Login opcional: só "Entrar com a Conta Conexus" e "Criar conta" (no navegador, pelo bff-auth). */
export default function Login() {
  const router = useRouter();
  const back = () => (router.canGoBack() ? router.back() : router.replace('/'));
  return (
    <ConexusLoginScreen
      appName="AntiSpam BR"
      message="Opcional: com a Conta Conexus você guarda um backup das listas, envia denúncias identificadas e acompanha doações. O app funciona inteiro sem ela."
      onSignedIn={() => router.replace('/account')}
      onGuest={back}
      onBack={back}
    />
  );
}
