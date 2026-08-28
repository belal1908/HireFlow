// Real production environment, swapped in for `ng build`'s default "production" configuration
// via angular.json's fileReplacements (mirrors how the "development" configuration already
// swaps in environment.development.ts). Points at the live Render-hosted backend.
export const environment = {
  production: true,
  apiUrl: 'https://hireflow-backend-c3mv.onrender.com'
};
