import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from '../models/api-error.model';

/**
 * Pulls the human-readable message out of a failed HttpClient call. Always prefers the
 * backend's own ApiError.message (e.g. TransitionValidator's denial reason) over anything
 * invented client-side, per the PRD ("surface the backend's error message, don't invent your
 * own"). Falls back to a generic message only when the body isn't the expected shape (e.g. a
 * network failure with no response body at all).
 */
export function extractErrorMessage(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    const body = err.error as ApiError | undefined;
    if (body?.message) {
      if (body.details && body.details.length > 0) {
        return `${body.message}: ${body.details.join('; ')}`;
      }
      return body.message;
    }
    if (err.status === 0) {
      return 'Could not reach the server. Is the backend running on ' + 'http://localhost:8080' + '?';
    }
    return `Request failed (HTTP ${err.status})`;
  }
  return 'An unexpected error occurred';
}
