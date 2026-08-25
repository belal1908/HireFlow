import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
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

  /** CANDIDATE only: their own applications. */
  mine(): Observable<ApplicationResponse[]> {
    return this.http.get<ApplicationResponse[]>(`${this.baseUrl}/mine`);
  }

  /** RECRUITER/ADMIN only: all applications, optionally filtered. */
  list(postingId?: number | null, status?: ApplicationStatus | null): Observable<ApplicationResponse[]> {
    let params = new HttpParams();
    if (postingId != null) {
      params = params.set('postingId', postingId);
    }
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<ApplicationResponse[]>(this.baseUrl, { params });
  }

  updateStatus(id: number, targetStatus: ApplicationStatus, note?: string): Observable<ApplicationResponse> {
    const body: StatusUpdateRequest = { targetStatus, note: note || undefined };
    return this.http.patch<ApplicationResponse>(`${this.baseUrl}/${id}/status`, body);
  }

  events(id: number): Observable<ApplicationEventResponse[]> {
    return this.http.get<ApplicationEventResponse[]>(`${this.baseUrl}/${id}/events`);
  }
}
