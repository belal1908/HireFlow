// Production environment. HireFlow's backend has no separate prod deployment yet (Week 1/2
// scope is local-only), so this intentionally matches environment.development.ts for now.
export const environment = {
  production: true,
  apiUrl: 'http://localhost:8080'
};
