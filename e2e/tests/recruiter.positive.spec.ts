import { test, expect } from '@playwright/test';
import { seedCandidate, seedPrivilegedUser } from '../utils/seed';
import { apply, createPosting } from '../utils/api';
import { loginViaUi } from '../utils/ui';

/**
 * PRD positive flow: log in -> see all applications -> advance one step (APPLIED -> SCREENING)
 * -> reject a DIFFERENT application -> view its audit trail. The two candidate applications are
 * seeded via direct API calls (this test is about the recruiter's UI, not the candidate's), then
 * every recruiter action below is driven through the real UI.
 */
test.describe('Recruiter journey (positive)', () => {
  test('advance one application, reject a different one, and view its audit trail', async ({ page, request }) => {
    const admin = await seedPrivilegedUser(request, 'ADMIN');
    const postingTitle = `E2E Recruiter Posting ${Date.now()}`;
    const posting = await createPosting(request, admin.auth.accessToken, postingTitle);

    const candidateA = await seedCandidate(request, 'recruiter-flow-a');
    const candidateB = await seedCandidate(request, 'recruiter-flow-b');
    const appA = await apply(request, candidateA.auth.accessToken, posting.id);
    const appB = await apply(request, candidateB.auth.accessToken, posting.id);

    const recruiter = await seedPrivilegedUser(request, 'RECRUITER');
    await loginViaUi(page, recruiter.email, recruiter.password);
    await expect(page).toHaveURL(/\/recruiter\/applications$/);

    const rowA = page.locator(`#app-row-${appA.id}`);
    const rowB = page.locator(`#app-row-${appB.id}`);
    await expect(rowA).toBeVisible();
    await expect(rowB).toBeVisible();

    // Advance application A one step: APPLIED -> SCREENING.
    await rowA.getByRole('button', { name: /Advance/ }).click();
    await expect(rowA.locator('.status-badge')).toHaveText('SCREENING');

    // Reject a DIFFERENT application (B) — A must stay untouched.
    await rowB.getByRole('button', { name: 'Reject' }).click();
    await expect(rowB.locator('.status-badge')).toHaveText('REJECTED');
    await expect(rowA.locator('.status-badge')).toHaveText('SCREENING');

    // View B's audit trail: the creation event (APPLIED) and the rejection event (-> REJECTED).
    await rowB.getByRole('button', { name: 'View events' }).click();
    const eventsList = page.locator(`#app-row-${appB.id} + tr.events-row .events-list`);
    await expect(eventsList).toBeVisible();
    const items = eventsList.locator('li');
    await expect(items).toHaveCount(2);
    await expect(items.nth(0)).toContainText('APPLIED');
    await expect(items.nth(1)).toContainText('REJECTED');
  });
});
