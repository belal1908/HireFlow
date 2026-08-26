/** Mirrors com.hireflow.common.dto.PageResponse<T> exactly — the shape of every paginated list endpoint. */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}
