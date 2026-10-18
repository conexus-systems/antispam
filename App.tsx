/**
 * Compat: o app usa expo-router (app/). Este arquivo existe apenas como
 * fallback de registro e redireciona para a rota raiz.
 */
import { Link } from 'expo-router';
import { useTheme } from './src/ui/theme';

export default function App() {
  const theme = useTheme();
  return (
    <Link href="/" style={[theme.type.body, { marginTop: 80, marginHorizontal: theme.gutter, color: theme.primary }]}>
      Abrir AntiSpam BR
    </Link>
  );
}
