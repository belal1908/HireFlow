import { test, expect } from '@playwright/test';
import { API_URL } from '../utils/env';
import { seedPrivilegedUser } from '../utils/seed';
import { createPosting, login, uniqueEmail, authHeader, updateStatus, DEFAULT_PASSWORD } from '../utils/api';

/**
 * PRD positive flow, rewritten for the ApplyTrack-styled redesign: register -> browse open
 * postings (`/postings`, unified route) -> apply -> see it in "Applications" (`/applications`,
 * also unified — CANDIDATE just sees only their own, enforced server-side) -> (after a
 * recruiter/admin seed advances it to OFFER via API setup) open the transition confirm-sheet and
 * accept the offer for real. Everything the CANDIDATE does is driven through the real UI, exactly
 * as before; the recruiter's advancement is still done via direct API calls, per the PRD's
 * "direct DB or API setup" allowance for seeding.
 *
 * What changed vs. the pre-redesign version of this test: `/` is Overview now (not a per-role
 * redirect to `/postings`), so navigation to Postings/Applications goes through the sidebar; the
 * old "Accept offer" button fired the transition immediately, the new one opens a confirm sheet
 * that must be confirmed; row selectors are `#app-row-{id}`/`#posting-card-{id}` instead of
 * `tr` text matches.
 */
test.describe('Candidate journey (positive)', () => {
  test('register, apply, see it in Applications, and accept an OFFER via the confirm sheet', async ({ page, request }) => {
    const admin = await seedPrivilegedUser(request, 'ADMIN');
    const postingTitle = `E2E Candidate Posting ${Date.now()}`;
    const posting = await createPosting(request, admin.auth.accessToken, postingTitle);

    const candidateEmail = uniqueEmail('candidate-ui');

    await page.goto('/register');
    await page.locator('#email').fill(candidateEmail);
    await page.locator('#password').fill(DEFAULT_PASSWORD);
    await page.getByRole('button', { name: /register/i }).click();
    // '/' is now the real Overview page for every role (no more per-role redirect).
    await expect(page).not.toHaveURL(/\/(login|register)/);

    await page.getByRole('link', { name: 'Job postings', exact: true }).click();
    const card = page.locator(`#posting-card-${posting.id}`);
    await expect(card).toBeVisible();
    await card.getByRole('button', { name: 'Apply', exact: true }).click();
    await expect(card.getByRole('button', { name: 'Already applied' })).toBeVisible();

    // Find this candidate's application id (via their own API token — same credentials just
    // used in the UI) so a seeded recruiter can advance it through the pipeline to OFFER.
    const candidateAuth = await login(request, candidateEmail, DEFAULT_PASSWORD);
    const mineRes = await request.get(`${API_URL}/api/applications/mine`, {
      headers: authHeader(candidateAuth.accessToken)
    });
    expect(mineRes.status()).toBe(200);
    const mine = (await mineRes.json()) as Array<{ id: number; jobPostingId: number }>;
    const application = mine.find((a) => a.jobPostingId === posting.id);
    expect(application, 'application for the seeded posting should exist').toBeTruthy();
    const applicationId = application!.id;

    await page.getByRole('link', { name: 'Applications', exact: true }).click();
    const row = page.locator(`#app-row-${applicationId}`);
    await expect(row).toBeVisible();
    await expect(row.locator('.status-badge')).toHaveText('APPLIED');

    const recruiter = await seedPrivilegedUser(request, 'RECRUITER');
    await updateStatus(request, recruiter.auth.accessToken, applicationId, 'SCREENING');
    await updateStatus(request, recruiter.auth.accessToken, applicationId, 'INTERVIEW');
    await updateStatus(request, recruiter.auth.accessToken, applicationId, 'OFFER');

    // ApplicationsComponent only fetches once in ngOnInit, so force a refetch by navigating away
    // and back through the real nav links — never page.reload()/page.goto(), which would wipe
    // the in-memory session (AuthService holds tokens in memory only, see README).
    await page.getByRole('link', { name: 'Overview', exact: true }).click();
    await page.getByRole('link', { name: 'Applications', exact: true }).click();
    const refreshedRow = page.locator(`#app-row-${applicationId}`);
    await expect(refreshedRow.locator('.status-badge')).toHaveText('OFFER');

    // Select the row to populate the detail panel, then open the confirm sheet — this redesign's
    // action buttons no longer fire the transition directly.
    await refreshedRow.click();
    const detailPanel = page.locator('.detail-panel');
    await expect(detailPanel.locator('.status-badge').first()).toHaveText('OFFER');
    await detailPanel.getByRole('button', { name: 'Accept offer', exact: true }).click();

    const modal = page.locator('.modal-overlay');
    await expect(modal).toBeVisible();
    await expect(modal.getByText('PATCH /api/applications/' + applicationId + '/status')).toBeVisible();
    await modal.getByRole('button', { name: 'Accept offer', exact: true }).click();

    await expect(modal).toBeHidden();
    await expect(refreshedRow.locator('.status-badge')).toHaveText('ACCEPTED');
    await expect(detailPanel.getByText(/No role can move this application again/)).toBeVisible();
  });
});
