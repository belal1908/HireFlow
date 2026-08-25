import { Client } from 'pg';
import { DB_CONFIG } from './env';

/**
 * Direct-SQL escape hatch for seeding RECRUITER/ADMIN accounts, per the README's documented
 * approach (`UPDATE users SET role = ... WHERE email = ...`) — self-registration always forces
 * CANDIDATE, so there is no UI/API path to the *first* recruiter/admin account other than this
 * or POST /api/admin/users (which itself requires an existing admin). Used only for test setup,
 * never as something under test.
 */
async function withDb<T>(fn: (client: Client) => Promise<T>): Promise<T> {
  const client = new Client(DB_CONFIG);
  await client.connect();
  try {
    return await fn(client);
  } finally {
    await client.end();
  }
}

export async function promoteUserRole(email: string, role: 'RECRUITER' | 'ADMIN'): Promise<void> {
  await withDb(async (client) => {
    const result = await client.query('UPDATE users SET role = $1 WHERE email = $2', [role, email]);
    if (result.rowCount !== 1) {
      throw new Error(`Expected to promote exactly 1 user with email ${email}, affected ${result.rowCount}`);
    }
  });
}
