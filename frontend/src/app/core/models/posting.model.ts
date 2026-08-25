/** Mirrors com.hireflow.posting.* exactly. */

export type PostingStatus = 'OPEN' | 'CLOSED';

export interface PostingResponse {
  id: number;
  title: string;
  description: string | null;
  status: PostingStatus;
  createdBy: number;
  createdAt: string;
}

export interface CreatePostingRequest {
  title: string;
  description?: string;
}

/** PATCH semantics: only non-null/omitted fields are applied server-side. */
export interface UpdatePostingRequest {
  title?: string;
  description?: string;
  status?: PostingStatus;
}
