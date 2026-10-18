// @expo/vector-icons (SDK 58) puxa expo-font/expo-asset, que não carregam no Jest deste app.
// Os testes não renderizam ícones: um componente vazio basta.
function Icon() {
  return null;
}
module.exports = Icon;
module.exports.default = Icon;
module.exports.Ionicons = Icon;
