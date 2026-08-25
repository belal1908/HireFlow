import { Page, expect } from '@playwright/test';
import { FRONTEND_URL } from './env';

/** Logs in through the real login form (not localStorage/token injection) and waits for the post-login redirect. */
export async function loginViaUi(page: Page, email: string, password: string): Promise<void> {
  await page.goto(`${FRONTEND_URL}/login`);
  await page.locator('#email').fill(email);
  await page.locator('#password').fill(password);
  await page.getByRole('button', { name: /log in/i }).click();
  // homeRedirectGuard sends '/' to a role-specific landing page once authService.currentUser() is set.
  await expect(page).not.toHaveURL(/\/login/);
}
