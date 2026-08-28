import { test, expect } from '@playwright/test';
import { seedPrivilegedUser } from '../utils/seed';
import { loginViaUi } from '../utils/ui';

/**
 * PRD positive flow, rewritten for the redesign: log in -> Job postings (`/postings`, unified
 * route — ADMIN gets "+ New posting" instead of the non-admin dashed 403-demo button) -> create a
 * posting through the new modal -> see it appear -> close it. All through the UI.
 */
test.describe('Admin journey (positive)', () => {
  test('create a posting, see it appear, and close it', async ({ page, request }) => {
    const admin = await seedPrivilegedUser(request, 'ADMIN');
    await loginViaUi(page, admin.email, admin.password);
    await page.getByRole('link', { name: 'Job postings', exact: true }).click();

    const title = `E2E Admin Posting ${Date.now()}`;
    await page.getByRole('button', { name: '+ New posting' }).click();

    const modal = page.locator('.modal-overlay');
    await expect(modal).toBeVisible();
    await modal.locator('input[formcontrolname="title"]').fill(title);
    await modal.locator('textarea[formcontrolname="description"]').fill('Created by the Playwright e2e suite');
    await modal.getByRole('button', { name: 'Create posting' }).click();
    await expect(modal).toBeHidden();

    const card = page.locator('.posting-card', { hasText: title });
    await expect(card).toBeVisible();
    await expect(card.locator('.posting-badge')).toHaveText('OPEN');

    await card.getByRole('button', { name: 'Close', exact: true }).click();
    await expect(card.locator('.posting-badge')).toHaveText('CLOSED');
    await expect(card.getByRole('button', { name: 'Reopen' })).toBeVisible();
  });
});
