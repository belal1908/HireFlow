/**
 * Central place for the URLs/credentials the e2e suite talks to. All default to the values
 * documented in the main README (local dev: `mvn spring-boot:run` + `ng serve`; or the
 * docker-compose "full" profile, which publishes the same ports to the host) so the suite runs
 * unmodified against either.
 */
export const FRONTEND_URL = process.env.FRONTEND_URL || 'http://localhost:4200';
export const API_URL = process.env.API_URL || 'http://localhost:8080';

export const DB_CONFIG = {
  host: process.env.DB_HOST || 'localhost',
  port: Number(process.env.DB_HOST_PORT || 5433),
  user: process.env.DB_USERNAME || 'hireflow',
  password: process.env.DB_PASSWORD || 'hireflow',
  database: process.env.DB_NAME || 'hireflow'
};
