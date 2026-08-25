import { test, expect } from '@playwright/test';
import { API_URL } from '../utils/env';
import { seedCandidate, seedPrivilegedUser } from '../utils/seed';
import { apply, authHeader, createPosting } from '../utils/api';

/**
 * The PRD's specific "not just an assumption" proof: these tests use Playwright's `request`
 * context directly — no browser page, no UI — with a real JWT obtained from the real
 * `/api/auth/login` endpoint, hitting the real backend. A CANDIDATE genuinely cannot access or
 * act on another candidate's application via a direct API call; this is enforced by
 * `ApplicationService` (ownership -> 403) and `TransitionValidator` (illegal transition -> 400),
 * independent of anything the UI does or doesn't render. See the README's "403 vs 400" design
 * note for why these are two different status codes rather than both being 403.
 */
test.describe('Direct API authorization proofs (negative) — bypasses the UI entirely', () => {
  test("a CANDIDATE cannot PATCH another candidate's application status (403, ownership)", async ({ request }) => {
    const admin = await seedPrivilegedUser(request, 'ADMIN');
    const posting = await createPosting(request, admin.auth.accessToken, `E2E API Security Posting ${Date.now()}`);

    const owner = await seedCandidate(request, 'api-sec-owner-a');
    const intruder = await seedCandidate(request, 'api-sec-intruder-a');
    const ownerApplication = await apply(request, owner.auth.accessToken, posting.id);

    const res = await request.patch(`${API_URL}/api/applications/${ownerApplication.id}/status`, {
      headers: authHeader(intruder.auth.accessToken),
      data: { targetStatus: 'WITHDRAWN' }
    });

    expect(res.status(), await res.text()).toBe(403);
  });

  test("a CANDIDATE cannot GET another candidate's application events (403, ownership)", async ({ request }) => {
    const admin = await seedPrivilegedUser(request, 'ADMIN');
    const posting = await createPosting(request, admin.auth.accessToken, `E2E API Security Posting ${Date.now()}`);

    const owner = await seedCandidate(request, 'api-sec-owner-b');
    const intruder = await seedCandidate(request, 'api-sec-intruder-b');
    const ownerApplication = await apply(request, owner.auth.accessToken, posting.id);

    const res = await request.get(`${API_URL}/api/applications/${ownerApplication.id}/events`, {
      headers: authHeader(intruder.auth.accessToken)
    });

    expect(res.status(), await res.text()).toBe(403);
  });

  test('a CANDIDATE attempting a RECRUITER-only transition on their OWN application gets 400, not 403', async ({ request }) => {
    const admin = await seedPrivilegedUser(request, 'ADMIN');
    const posting = await createPosting(request, admin.auth.accessToken, `E2E API Security Posting ${Date.now()}`);
    const candidate = await seedCandidate(request, 'api-sec-selftransition');
    const application = await apply(request, candidate.auth.accessToken, posting.id);

    // APPLIED -> SCREENING is RECRUITER-only. The candidate owns this application (ownership
    // check passes), but TransitionValidator denies the role for this target status: 400, per
    // the "validator's domain" design note — distinct from the ownership 403 cases above.
    const res = await request.patch(`${API_URL}/api/applications/${application.id}/status`, {
      headers: authHeader(candidate.auth.accessToken),
      data: { targetStatus: 'SCREENING' }
    });

    expect(res.status(), await res.text()).toBe(400);
  });

  test('an unauthenticated request to a protected endpoint gets 401', async ({ request }) => {
    const res = await request.get(`${API_URL}/api/applications`);

    expect(res.status()).toBe(401);
  });

  test('a non-ADMIN cannot PATCH a posting directly (403)', async ({ request }) => {
    // This is the specific check the README's "weaken a @PreAuthorize annotation" proof relies
    // on: PostingController#update is @PreAuthorize("hasRole('ADMIN')"). None of the other tests
    // in this suite exercise that endpoint's authorization directly (admin.positive.spec.ts only
    // ever calls it *as* an admin), so without this test, removing that annotation would not
    // turn any test red.
    const admin = await seedPrivilegedUser(request, 'ADMIN');
    const posting = await createPosting(request, admin.auth.accessToken, `E2E API Security Posting ${Date.now()}`);
    const candidate = await seedCandidate(request, 'api-sec-posting-patch');

    const res = await request.patch(`${API_URL}/api/postings/${posting.id}`, {
      headers: authHeader(candidate.auth.accessToken),
      data: { status: 'CLOSED' }
    });

    expect(res.status(), await res.text()).toBe(403);
  });
});
