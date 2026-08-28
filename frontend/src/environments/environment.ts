// Default environment file - only actually used when a build configuration doesn't swap it
// out via fileReplacements. The real "production" configuration (see angular.json) replaces
// this with environment.production.ts; "development" replaces it with
// environment.development.ts. Kept pointed at localhost so an unconfigured/unknown build
// configuration fails obviously (connection refused) rather than silently hitting production.
export const environment = {
  production: true,
  apiUrl: 'http://localhost:8080'
};
