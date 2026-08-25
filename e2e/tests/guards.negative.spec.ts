import { test, expect } from '@playwright/test';
import { seedCandidate } from '../utils/seed';

/**
 * PRD negative requirement: a CANDIDATE hitting an ADMIN/RECRUITER route directly by URL gets
 * redirected by the route guard — asserted on the resulting URL (and the /forbidden page's own
 * content), not on "the button isn't shown". This is `roleGuard`
 * (frontend/src/app/core/guards/role.guard.ts) actually blocking activation.
 *
 * Note on flow: AuthService keeps tokens in memory only (see README), so a *hard* browser
 * navigation (typing a URL and hitting enter) always drops any existing session first — exactly
 * like a real user would experience. So "hit a guarded URL directly" here means: land there
 * unauthenticated (guard sends you to /login?redirect=<url>), log in as the CANDIDATE, and let
 * LoginComponent's own redirect-after-login send you back to the originally-requested URL —
 * which is when roleGuard's *role* check (not just its login check) actually fires, and denies.
 */
test.describe('Route guards (negative)', () => {
  test('a CANDIDATE hitting /admin/postings directly is redirected to /forbidden', async ({ page, request }) => {
    const candidate = await seedCandidate(request, 'guard-admin');

    await page.goto('/admin/postings');
    await expect(page).toHaveURL(/\/login\?redirect=%2Fadmin%2Fpostings/);

    await page.locator('#email').fill(candidate.email);
    await page.locator('#password').fill(candidate.password);
    await page.getByRole('button', { name: /log in/i }).click();

    await expect(page).toHaveURL(/\/forbidden$/);
    await expect(page.getByRole('heading', { name: /403/ })).toBeVisible();
  });

  test('a CANDIDATE hitting /recruiter/applications directly is redirected to /forbidden', async ({ page, request }) => {
    const candidate = await seedCandidate(request, 'guard-recruiter');

    await page.goto('/recruiter/applications');
    await expect(page).toHaveURL(/\/login\?redirect=%2Frecruiter%2Fapplications/);

    await page.locator('#email').fill(candidate.email);
    await page.locator('#password').fill(candidate.password);
    await page.getByRole('button', { name: /log in/i }).click();

    await expect(page).toHaveURL(/\/forbidden$/);
  });

  test('an unauthenticated visitor hitting a guarded route directly is redirected to /login', async ({ page }) => {
    await page.goto('/postings');

    await expect(page).toHaveURL(/\/login/);
  });
});
