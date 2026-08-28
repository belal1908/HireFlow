import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageResponse } from '../models/page.model';
import { CreatePostingRequest, PostingResponse, UpdatePostingRequest } from '../models/posting.model';

@Injectable({ providedIn: 'root' })
export class PostingService {
  private readonly baseUrl = `${environment.apiUrl}/api/postings`;

  constructor(private http: HttpClient) {}

  /** Any authenticated user; backend filters to OPEN-only for non-ADMIN. Paginated (default size=20 server-side). */
  list(page = 0, size = 20): Observable<PageResponse<PostingResponse>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<PostingResponse>>(this.baseUrl, { params });
  }

  /**
   * Convenience over `list()` for the several pages (Overview, Applications) that only need
   * "posting id -> title" to label a row and don't care about pagination — same "one generous
   * fetch" pattern the pre-redesign components already used for this exact purpose.
   */
  titleMap(): Observable<Map<number, PostingResponse>> {
    return this.list(0, 500).pipe(map((page) => new Map(page.content.map((p) => [p.id, p]))));
  }

  create(request: CreatePostingRequest): Observable<PostingResponse> {
    return this.http.post<PostingResponse>(this.baseUrl, request);
  }

  update(id: number, request: UpdatePostingRequest): Observable<PostingResponse> {
    return this.http.patch<PostingResponse>(`${this.baseUrl}/${id}`, request);
  }
}
