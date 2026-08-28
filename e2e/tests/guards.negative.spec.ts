import { test, expect } from '@playwright/test';
import { seedCandidate, seedPrivilegedUser } from '../utils/seed';

/**
 * PRD negative requirement, rewritten for the redesign's unified routes: the old per-role pages
 * (`/admin/postings`, `/recruiter/applications`) are gone, and `/`, `/applications`,
 * `/state-machine`, `/postings`, `/settings` are now reachable by any authenticated role (they
 * render role-adaptive content instead of being gated). The one route that's still genuinely
 * role-exclusive is `/admin/users` (ADMIN-only user management) — this is `roleGuard('ADMIN')`
 * (frontend/src/app/app.routes.ts) actually blocking activation, asserted on the resulting URL
 * (and the /forbidden page's own content), not on "the button isn't shown."
 *
 * Note on flow: AuthService keeps tokens in memory only (see README), so a *hard* browser
 * navigation (typing a URL and hitting enter) always drops any existing session first — exactly
 * like a real user would experience. So "hit a guarded URL directly" here means: land there
 * unauthenticated (guard sends you to /login?redirect=<url>), log in, and let LoginComponent's
 * own redirect-after-login send you back to the originally-requested URL — which is when
 * roleGuard's *role* check (not just its login check) actually fires, and denies.
 */
test.describe('Route guards (negative)', () => {
  test('a CANDIDATE hitting /admin/users directly is redirected to /forbidden', async ({ page, request }) => {
    const candidate = await seedCandidate(request, 'guard-admin');

    await page.goto('/admin/users');
    await expect(page).toHaveURL(/\/login\?redirect=%2Fadmin%2Fusers/);

    await page.locator('#email').fill(candidate.email);
    await page.locator('#password').fill(candidate.password);
    await page.getByRole('button', { name: /sign in/i }).click();

    await expect(page).toHaveURL(/\/forbidden$/);
    await expect(page.getByRole('heading', { name: 'Forbidden' })).toBeVisible();
    await expect(page.getByText('403', { exact: true })).toBeVisible();
  });

  test('a RECRUITER hitting /admin/users directly is also redirected to /forbidden', async ({ page, request }) => {
    const recruiter = await seedPrivilegedUser(request, 'RECRUITER');

    await page.goto('/admin/users');
    await expect(page).toHaveURL(/\/login\?redirect=%2Fadmin%2Fusers/);

    await page.locator('#email').fill(recruiter.email);
    await page.locator('#password').fill(recruiter.password);
    await page.getByRole('button', { name: /sign in/i }).click();

    await expect(page).toHaveURL(/\/forbidden$/);
  });

  test('an unauthenticated visitor hitting a guarded route directly is redirected to /login', async ({ page }) => {
    await page.goto('/applications');

    await expect(page).toHaveURL(/\/login/);
  });
});
