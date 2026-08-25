import { test, expect } from '@playwright/test';
import { seedPrivilegedUser } from '../utils/seed';
import { loginViaUi } from '../utils/ui';

/** PRD positive flow: log in -> create a posting -> see it appear -> close it. All through the UI. */
test.describe('Admin journey (positive)', () => {
  test('create a posting, see it appear, and close it', async ({ page, request }) => {
    const admin = await seedPrivilegedUser(request, 'ADMIN');
    await loginViaUi(page, admin.email, admin.password);
    await expect(page).toHaveURL(/\/admin\/postings$/);

    const title = `E2E Admin Posting ${Date.now()}`;
    await page.locator('#new-title').fill(title);
    await page.locator('#new-description').fill('Created by the Playwright e2e suite');
    await page.getByRole('button', { name: 'Create posting' }).click();

    const row = page.locator('tr', { hasText: title });
    await expect(row).toBeVisible();
    await expect(row.locator('.status-badge')).toHaveText('OPEN');

    await row.getByRole('button', { name: 'Close' }).click();
    await expect(row.locator('.status-badge')).toHaveText('CLOSED');
    await expect(row.getByRole('button', { name: 'Reopen' })).toBeVisible();
  });
});
