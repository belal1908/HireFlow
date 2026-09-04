// Throwaway script to capture README screenshots against the live Render deploy.
// Not part of the test suite. Run with: node screenshot-live.js
const { chromium } = require('@playwright/test');

const BASE = 'https://hireflow-backend-c3mv.onrender.com';
const OUT = '../docs/screenshots';

async function main() {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });

  // Log in as the seeded demo admin.
  await page.goto(`${BASE}/login`);
  await page.getByLabel('Email').fill('admin@hireflow.demo');
  await page.getByLabel('Password').fill('DemoAdmin2026!');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.waitForURL(`${BASE}/`);
  await page.waitForTimeout(2500);
  await page.screenshot({ path: `${OUT}/overview.png` });

  // Make sure there's at least one posting to show, so the screenshot isn't an empty state.
  await page.getByRole('link', { name: 'Job postings' }).click();
  await page.waitForURL(`${BASE}/postings`);
  await page.waitForTimeout(2000);
  const hasPosting = await page.locator('text=Senior').count();
  if (hasPosting === 0) {
    await page.getByRole('button', { name: '+ New posting' }).click();
    await page.waitForTimeout(500);
    await page.locator('.plain-input').fill('Senior Backend Engineer');
    await page.locator('.plain-textarea').fill('Own the payments pipeline. Remote-friendly, small team.');
    await page.locator('.modal-card button[type="submit"], .modal-card button.btn-ink').last().click();
    await page.waitForTimeout(1500);
  }
  await page.screenshot({ path: `${OUT}/postings.png` });

  await page.getByRole('link', { name: 'State machine' }).click();
  await page.waitForURL(`${BASE}/state-machine`);
  await page.waitForTimeout(1500);
  await page.screenshot({ path: `${OUT}/state-machine.png` });

  await page.getByRole('link', { name: 'Settings' }).click();
  await page.waitForURL(`${BASE}/settings`);
  await page.waitForTimeout(1500);
  await page.screenshot({ path: `${OUT}/settings.png` });

  await browser.close();

  // Logged-out forgot-password screen, separate context.
  const browser2 = await chromium.launch();
  const outPage = await browser2.newPage({ viewport: { width: 1440, height: 900 } });
  await outPage.goto(`${BASE}/forgot-password`);
  await outPage.waitForTimeout(1000);
  await outPage.screenshot({ path: `${OUT}/forgot-password.png` });
  await browser2.close();

  console.log('done');
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
