/** Mirrors com.hireflow.common.dto.ApiError exactly — the body GlobalExceptionHandler renders. */
export interface ApiError {
  status: number;
  error: string;
  message: string;
  timestamp: string;
  details: string[] | null;
}
