import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageResponse } from '../models/page.model';
import { CreateUserRequest, Role, UserResponse } from '../models/user.model';

/** ADMIN-only account management — mirrors PostingService's shape/conventions exactly. */
@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly baseUrl = `${environment.apiUrl}/api/admin/users`;

  constructor(private http: HttpClient) {}

  /** ADMIN only; backend supports an optional `?role=` filter, paginated (default size=20 server-side). */
  list(role?: Role, page = 0, size = 20): Observable<PageResponse<UserResponse>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (role) {
      params = params.set('role', role);
    }
    return this.http.get<PageResponse<UserResponse>>(this.baseUrl, { params });
  }

  create(request: CreateUserRequest): Observable<UserResponse> {
    return this.http.post<UserResponse>(this.baseUrl, request);
  }
}
