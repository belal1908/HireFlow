// Real production environment, swapped in for `ng build`'s default "production" configuration
// via angular.json's fileReplacements. Backend and frontend are now served from the same
// deployment (see the root Dockerfile's frontend-build stage and SpaWebConfig on the backend),
// so apiUrl is relative rather than a hardcoded backend hostname - no more predicting Render's
// URL at build time, and no CORS involved since every request is same-origin.
export const environment = {
  production: true,
  apiUrl: ''
};
