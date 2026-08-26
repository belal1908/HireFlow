import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageResponse } from '../models/page.model';
import {
  ApplicationEventResponse,
  ApplicationResponse,
  ApplicationStatus,
  ApplyRequest,
  StatusUpdateRequest
} from '../models/application.model';

@Injectable({ providedIn: 'root' })
export class ApplicationService {
  private readonly baseUrl = `${environment.apiUrl}/api/applications`;

  constructor(private http: HttpClient) {}

  apply(jobPostingId: number): Observable<ApplicationResponse> {
    const body: ApplyRequest = { jobPostingId };
    return this.http.post<ApplicationResponse>(this.baseUrl, body);
  }

  /** CANDIDATE only: their own applications. Not paginated server-side (see README) — a single candidate's list stays small. */
  mine(): Observable<ApplicationResponse[]> {
    return this.http.get<ApplicationResponse[]>(`${this.baseUrl}/mine`);
  }

  /** RECRUITER/ADMIN only: all applications, optionally filtered, paginated (default size=20 server-side). */
  list(
    postingId?: number | null,
    status?: ApplicationStatus | null,
    page = 0,
    size = 20
  ): Observable<PageResponse<ApplicationResponse>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (postingId != null) {
      params = params.set('postingId', postingId);
    }
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<PageResponse<ApplicationResponse>>(this.baseUrl, { params });
  }

  updateStatus(id: number, targetStatus: ApplicationStatus, note?: string): Observable<ApplicationResponse> {
    const body: StatusUpdateRequest = { targetStatus, note: note || undefined };
    return this.http.patch<ApplicationResponse>(`${this.baseUrl}/${id}/status`, body);
  }

  events(id: number): Observable<ApplicationEventResponse[]> {
    return this.http.get<ApplicationEventResponse[]>(`${this.baseUrl}/${id}/events`);
  }

  /** CANDIDATE, owner-only. Re-uploading replaces the previous file (enforced server-side). */
  uploadResume(id: number, file: File): Observable<ApplicationResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ApplicationResponse>(`${this.baseUrl}/${id}/resume`, formData);
  }

  /** Owning CANDIDATE, or RECRUITER/ADMIN. Fetches the raw PDF bytes plus headers for filename/content-type. */
  downloadResume(id: number): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.baseUrl}/${id}/resume`, { observe: 'response', responseType: 'blob' });
  }
}
