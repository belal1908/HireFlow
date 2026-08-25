import { defineConfig, devices } from '@playwright/test';
import { FRONTEND_URL } from './utils/env';

/**
 * No `webServer` block on purpose: this suite runs against a stack you bring up yourself first,
 * either local dev (`mvn spring-boot:run` + `ng serve`, Postgres via `docker compose up -d`) or
 * the full docker-compose "full" profile (`docker compose --profile full up`). Both publish the
 * same ports (frontend 4200, backend 8080, Postgres 5433), so the suite runs unmodified against
 * either — see the README's "End-to-end tests" section.
 */
export default defineConfig({
  testDir: './tests',
  // Tests seed real accounts/postings against one shared Postgres instance and aren't isolated
  // from each other by a DB reset; keep them serial rather than chasing cross-test parallelism
  // that this portfolio-scoped suite doesn't need.
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: !!process.env.CI,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : [['list']],
  timeout: 30_000,
  expect: {
    timeout: 5_000
  },
  use: {
    baseURL: FRONTEND_URL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure'
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] }
    }
  ]
});
