import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreatePostingRequest, PostingResponse, UpdatePostingRequest } from '../models/posting.model';

@Injectable({ providedIn: 'root' })
export class PostingService {
  private readonly baseUrl = `${environment.apiUrl}/api/postings`;

  constructor(private http: HttpClient) {}

  /** Any authenticated user; backend filters to OPEN-only for non-ADMIN. */
  list(): Observable<PostingResponse[]> {
    return this.http.get<PostingResponse[]>(this.baseUrl);
  }

  create(request: CreatePostingRequest): Observable<PostingResponse> {
    return this.http.post<PostingResponse>(this.baseUrl, request);
  }

  update(id: number, request: UpdatePostingRequest): Observable<PostingResponse> {
    return this.http.patch<PostingResponse>(`${this.baseUrl}/${id}`, request);
  }
}
