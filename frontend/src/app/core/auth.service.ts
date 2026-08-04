import { Injectable, computed, signal } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, tap, catchError, throwError } from 'rxjs';

import { environment } from '../../environments/environment';
import {
  LoginRequest,
  LoginResponse,
  RefreshResponse,
  RegisterRequest,
  RegisterResponse,
  VerifyTwoFaRequest,
} from './models/auth.models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly baseUrl = environment.authApiUrl;
  private readonly accessTokenSignal = signal<string | null>(null);
  private preAuthToken: string | null = null;

  readonly accessToken = this.accessTokenSignal.asReadonly();
  readonly isLoggedIn = computed(() => this.accessTokenSignal() !== null);
  readonly userId = computed(() => this.decodeUserId(this.accessTokenSignal()));

  constructor(private readonly http: HttpClient) {}

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${this.baseUrl}/login`, request, { withCredentials: true })
      .pipe(
        tap((response) => {
          if (response.status === 'SUCCESS') {
            this.accessTokenSignal.set(response.access_token);
          } else {
            this.preAuthToken = response.pre_auth_token;
          }
        }),
      );
  }

  verifyTwoFa(request: VerifyTwoFaRequest): Observable<LoginResponse> {
    const headers = new HttpHeaders({ Authorization: `Bearer ${this.preAuthToken}` });
    return this.http
      .post<LoginResponse>(`${this.baseUrl}/verify-2fa/sms`, request, {
        headers,
        withCredentials: true,
      })
      .pipe(
        tap((response) => {
          if (response.status === 'SUCCESS') {
            this.preAuthToken = null;
            this.accessTokenSignal.set(response.access_token);
          }
        }),
      );
  }

  refresh(): Observable<RefreshResponse> {
    return this.http
      .post<RefreshResponse>(`${this.baseUrl}/refresh`, null, { withCredentials: true })
      .pipe(tap((response) => this.accessTokenSignal.set(response.access_token)));
  }

  logout(): Observable<unknown> {
    return this.http.post(`${this.baseUrl}/logout`, null).pipe(
      tap(() => this.clearSession()),
      catchError((error) => {
        this.clearSession();
        return throwError(() => error);
      }),
    );
  }

  register(request: RegisterRequest): Observable<RegisterResponse> {
    return this.http.post<RegisterResponse>(`${this.baseUrl}/register`, request);
  }

  clearSession(): void {
    this.preAuthToken = null;
    this.accessTokenSignal.set(null);
  }

  private decodeUserId(token: string | null): number | null {
    if (!token) {
      return null;
    }
    try {
      const payloadSegment = token.split('.')[1];
      const base64 = payloadSegment.replace(/-/g, '+').replace(/_/g, '/');
      const payload = JSON.parse(atob(base64));
      return typeof payload.userId === 'number' ? payload.userId : null;
    } catch {
      return null;
    }
  }
}
