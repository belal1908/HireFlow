/** Mirrors com.hireflow.application.* exactly. */

export type ApplicationStatus =
  | 'APPLIED'
  | 'SCREENING'
  | 'INTERVIEW'
  | 'OFFER'
  | 'ACCEPTED'
  | 'REJECTED'
  | 'WITHDRAWN';

export const TERMINAL_STATUSES: ReadonlySet<ApplicationStatus> = new Set(['ACCEPTED', 'REJECTED', 'WITHDRAWN']);

/** APPLIED -> SCREENING -> INTERVIEW -> OFFER -> ACCEPTED, one step at a time (RECRUITER-driven). */
const FORWARD_PATH: ApplicationStatus[] = ['APPLIED', 'SCREENING', 'INTERVIEW', 'OFFER'];

/** The single legal forward step for a RECRUITER from `current`, or null if none exists. */
export function nextForwardStatus(current: ApplicationStatus): ApplicationStatus | null {
  const index = FORWARD_PATH.indexOf(current);
  if (index === -1 || index === FORWARD_PATH.length - 1) {
    return null;
  }
  return FORWARD_PATH[index + 1];
}

export interface ApplicationResponse {
  id: number;
  candidateId: number;
  jobPostingId: number;
  status: ApplicationStatus;
  createdAt: string;
  updatedAt: string;
  /** Null until a resume has been uploaded (POST /api/applications/{id}/resume). */
  resumeFilename: string | null;
  resumeContentType: string | null;
  resumeSizeBytes: number | null;
  resumeUploadedAt: string | null;
}

export interface ApplicationEventResponse {
  id: number;
  applicationId: number;
  fromStatus: ApplicationStatus | null;
  toStatus: ApplicationStatus;
  changedBy: number;
  note: string | null;
  timestamp: string;
}

export interface ApplyRequest {
  jobPostingId: number;
}

export interface StatusUpdateRequest {
  targetStatus: ApplicationStatus;
  note?: string;
}
