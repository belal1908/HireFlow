import { test, expect } from '@playwright/test';
import { seedCandidate, seedPrivilegedUser } from '../utils/seed';
import { apply, createPosting } from '../utils/api';
import { loginViaUi } from '../utils/ui';

/**
 * PRD positive flow, rewritten for the redesign: log in -> land on Overview -> Applications
 * (unified route, RECRUITER sees every application) -> advance one step (APPLIED -> SCREENING)
 * through the new transition confirm-sheet -> reject a DIFFERENT application the same way ->
 * view its audit trail (now always visible in the detail panel for whichever row is selected,
 * rather than a per-row toggle). The two candidate applications are seeded via direct API calls
 * (this test is about the recruiter's UI, not the candidate's); every recruiter action below is
 * driven through the real UI.
 *
 * A second test in this file exercises the "real 403 demo" on `/postings` — RECRUITER is a
 * non-admin, so it gets the same dashed "Call POST /api/postings as RECRUITER" button and
 * genuine-403 panel as CANDIDATE (per the design's `cannotManagePostings = !isAdmin`).
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
    await page.getByRole('link', { name: 'Applications', exact: true }).click();

    const rowA = page.locator(`#app-row-${appA.id}`);
    const rowB = page.locator(`#app-row-${appB.id}`);
    await expect(rowA).toBeVisible();
    await expect(rowB).toBeVisible();

    const detailPanel = page.locator('.detail-panel');
    const modal = page.locator('.modal-overlay');

    // Advance application A one step: APPLIED -> SCREENING, via the confirm sheet.
    await rowA.click();
    await detailPanel.getByRole('button', { name: /Advance to screening/i }).click();
    await expect(modal).toBeVisible();
    await modal.getByRole('button', { name: 'Confirm transition', exact: true }).click();
    await expect(modal).toBeHidden();
    await expect(rowA.locator('.status-badge')).toHaveText('SCREENING');

    // Reject a DIFFERENT application (B) — A must stay untouched.
    await rowB.click();
    await detailPanel.getByRole('button', { name: 'Reject', exact: true }).click();
    await expect(modal).toBeVisible();
    await modal.getByRole('button', { name: 'Reject application', exact: true }).click();
    await expect(modal).toBeHidden();
    await expect(rowB.locator('.status-badge')).toHaveText('REJECTED');
    await expect(rowA.locator('.status-badge')).toHaveText('SCREENING');

    // B's audit trail is already showing in the detail panel (it's the selected row): the
    // creation event (APPLIED) and the rejection event (-> REJECTED).
    const trail = page.locator(`#events-${appB.id}`);
    await expect(trail).toBeVisible();
    const rows = trail.locator('.timeline-row');
    await expect(rows).toHaveCount(2);
    await expect(rows.nth(0)).toContainText('APPLIED');
    await expect(rows.nth(1)).toContainText('REJECTED');
  });

  test('the 403 demo on Job postings shows the real backend response for a non-admin', async ({ page, request }) => {
    const recruiter = await seedPrivilegedUser(request, 'RECRUITER');
    await loginViaUi(page, recruiter.email, recruiter.password);
    await page.getByRole('link', { name: 'Job postings', exact: true }).click();

    await page.getByRole('button', { name: /Call POST \/api\/postings as RECRUITER/i }).click();

    const panel = page.locator('.forbidden-panel');
    await expect(panel).toBeVisible();
    await expect(panel).toContainText('403');
    await expect(panel).toContainText('Forbidden — and not because the button was hidden.');

    await panel.getByRole('button', { name: 'Dismiss' }).click();
    await expect(panel).toBeHidden();
  });
});
