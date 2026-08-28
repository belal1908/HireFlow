import { Page, expect } from '@playwright/test';
import { FRONTEND_URL } from './env';

/**
 * Logs in through the real login form (not localStorage/token injection) and waits for the
 * post-login redirect. The redesign's button label is "Sign in" (design README section 1),
 * not the pre-redesign "Log in" — the field ids (#email/#password) are unchanged.
 *
 * Since the redesign, `/` is a real page (Overview) for every role rather than a per-role
 * redirect target (the old `homeRedirectGuard` is gone — see app.routes.ts), so this just
 * confirms we left /login, not that we landed on any particular route.
 */
export async function loginViaUi(page: Page, email: string, password: string): Promise<void> {
  await page.goto(`${FRONTEND_URL}/login`);
  await page.locator('#email').fill(email);
  await page.locator('#password').fill(password);
  await page.getByRole('button', { name: /sign in/i }).click();
  await expect(page).not.toHaveURL(/\/login/);
}

/** Selects a sidebar nav item by its visible label (Overview / Applications / State machine / Job postings / Settings). */
export async function goToNav(page: Page, label: string): Promise<void> {
  await page.getByRole('link', { name: label, exact: true }).click();
}
