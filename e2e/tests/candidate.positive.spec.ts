import { test, expect } from '@playwright/test';
import { API_URL } from '../utils/env';
import { seedPrivilegedUser } from '../utils/seed';
import { createPosting, login, uniqueEmail, authHeader, updateStatus, DEFAULT_PASSWORD } from '../utils/api';

/**
 * PRD positive flow: register -> browse open postings -> apply -> see it in "My Applications"
 * -> (after a recruiter/admin seed advances it to OFFER via API setup) accept the offer.
 * Everything the CANDIDATE does is driven through the real UI; the recruiter's advancement is
 * done via direct API calls, per the PRD's "direct DB or API setup" allowance for seeding.
 */
test.describe('Candidate journey (positive)', () => {
  test('register, apply, see it in My Applications, and accept an OFFER', async ({ page, request }) => {
    const admin = await seedPrivilegedUser(request, 'ADMIN');
    const postingTitle = `E2E Candidate Posting ${Date.now()}`;
    const posting = await createPosting(request, admin.auth.accessToken, postingTitle);

    const candidateEmail = uniqueEmail('candidate-ui');

    await page.goto('/register');
    await page.locator('#email').fill(candidateEmail);
    await page.locator('#password').fill(DEFAULT_PASSWORD);
    await page.getByRole('button', { name: /register/i }).click();
    await expect(page).toHaveURL(/\/postings$/);

    const card = page.locator('.card', { hasText: postingTitle });
    await expect(card).toBeVisible();
    await card.getByRole('button', { name: 'Apply', exact: true }).click();
    await expect(card.getByRole('button', { name: 'Already applied' })).toBeVisible();

    // Navigate via the real nav link (client-side SPA routing), not page.goto() — AuthService
    // holds tokens in memory only (see README), so a hard navigation would wipe the session.
    await page.getByRole('link', { name: 'My Applications' }).click();
    const row = page.locator('tr', { hasText: postingTitle });
    await expect(row).toBeVisible();
    await expect(row.locator('.status-badge')).toHaveText('APPLIED');

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

    const recruiter = await seedPrivilegedUser(request, 'RECRUITER');
    await updateStatus(request, recruiter.auth.accessToken, application!.id, 'SCREENING');
    await updateStatus(request, recruiter.auth.accessToken, application!.id, 'INTERVIEW');
    await updateStatus(request, recruiter.auth.accessToken, application!.id, 'OFFER');

    // MyApplicationsComponent only fetches once in ngOnInit, so force a refetch by navigating
    // away and back through the real nav links — again avoiding page.reload()/page.goto(),
    // which would wipe the in-memory session.
    await page.getByRole('link', { name: 'Postings' }).click();
    await page.getByRole('link', { name: 'My Applications' }).click();
    const refreshedRow = page.locator('tr', { hasText: postingTitle });
    await expect(refreshedRow.locator('.status-badge')).toHaveText('OFFER');
    await refreshedRow.getByRole('button', { name: 'Accept offer' }).click();
    await expect(refreshedRow.locator('.status-badge')).toHaveText('ACCEPTED');
    await expect(refreshedRow.getByText('No actions available')).toBeVisible();
  });
});
