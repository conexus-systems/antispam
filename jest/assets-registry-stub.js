/**
 * Stub para o jest-expo 57: o React Native 0.87 removeu `@react-native/assets-registry`
 * (agora `@react-native/asset-utils`), mas o setup do preset ainda faz jest.mock desse caminho.
 * O próprio preset substitui estas funções por mocks; aqui só garantimos que o módulo resolve.
 */
module.exports = {
  registerAsset: () => 1,
  getAssetByID: () => undefined,
};
