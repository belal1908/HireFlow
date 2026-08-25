import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreateUserRequest, Role, UserResponse } from '../models/user.model';

/** ADMIN-only account management — mirrors PostingService's shape/conventions exactly. */
@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly baseUrl = `${environment.apiUrl}/api/admin/users`;

  constructor(private http: HttpClient) {}

  /** ADMIN only; backend supports an optional `?role=` filter. */
  list(role?: Role): Observable<UserResponse[]> {
    const params = role ? { role } : undefined;
    return this.http.get<UserResponse[]>(this.baseUrl, { params });
  }

  create(request: CreateUserRequest): Observable<UserResponse> {
    return this.http.post<UserResponse>(this.baseUrl, request);
  }
}
