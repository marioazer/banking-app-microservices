import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';

import { AuthService } from './auth.service';
import { environment } from '../../environments/environment';

function makeFakeJwt(payload: Record<string, unknown>): string {
  const base64url = (obj: Record<string, unknown>) =>
    btoa(JSON.stringify(obj)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${base64url({ alg: 'HS256' })}.${base64url(payload)}.signature`;
}

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AuthService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('is created with no logged-in user', () => {
    expect(service.isLoggedIn()).toBeFalse();
    expect(service.accessToken()).toBeNull();
  });

  it('stores the access token and reports logged in on SUCCESS login', () => {
    service.login({ username: 'jdoe', password: 'secret123' }).subscribe();

    const req = httpMock.expectOne(`${environment.authApiUrl}/login`);
    expect(req.request.method).toBe('POST');
    expect(req.request.withCredentials).toBeTrue();
    req.flush({ status: 'SUCCESS', access_token: 'token-abc' });

    expect(service.accessToken()).toBe('token-abc');
    expect(service.isLoggedIn()).toBeTrue();
  });

  it('does not store an access token when 2FA is required', () => {
    service.login({ username: 'jdoe', password: 'secret123' }).subscribe();

    const req = httpMock.expectOne(`${environment.authApiUrl}/login`);
    req.flush({ status: '2FA_REQUIRED', pre_auth_token: 'pre-auth-xyz' });

    expect(service.accessToken()).toBeNull();
    expect(service.isLoggedIn()).toBeFalse();
  });

  it('verifies a 2FA code using the pre-auth token and stores the resulting access token', () => {
    service.login({ username: 'jdoe', password: 'secret123' }).subscribe();
    httpMock
      .expectOne(`${environment.authApiUrl}/login`)
      .flush({ status: '2FA_REQUIRED', pre_auth_token: 'pre-auth-xyz' });

    service.verifyTwoFa({ code: '123456' }).subscribe();
    const req = httpMock.expectOne(`${environment.authApiUrl}/verify-2fa/sms`);
    expect(req.request.headers.get('Authorization')).toBe('Bearer pre-auth-xyz');
    expect(req.request.withCredentials).toBeTrue();
    req.flush({ status: 'SUCCESS', access_token: 'full-token-123' });

    expect(service.accessToken()).toBe('full-token-123');
    expect(service.isLoggedIn()).toBeTrue();
  });

  it('refreshes the access token using the refresh-token cookie', () => {
    service.refresh().subscribe();

    const req = httpMock.expectOne(`${environment.authApiUrl}/refresh`);
    expect(req.request.method).toBe('POST');
    expect(req.request.withCredentials).toBeTrue();
    req.flush({ access_token: 'refreshed-token' });

    expect(service.accessToken()).toBe('refreshed-token');
  });

  it('clears the access token on logout', () => {
    service.login({ username: 'jdoe', password: 'secret123' }).subscribe();
    httpMock.expectOne(`${environment.authApiUrl}/login`).flush({ status: 'SUCCESS', access_token: 'token-abc' });

    service.logout().subscribe();
    const req = httpMock.expectOne(`${environment.authApiUrl}/logout`);
    req.flush({});

    expect(service.accessToken()).toBeNull();
    expect(service.isLoggedIn()).toBeFalse();
  });

  it('clears the access token even if the logout request fails', () => {
    service.login({ username: 'jdoe', password: 'secret123' }).subscribe();
    httpMock.expectOne(`${environment.authApiUrl}/login`).flush({ status: 'SUCCESS', access_token: 'token-abc' });

    service.logout().subscribe({ error: () => {} });
    const req = httpMock.expectOne(`${environment.authApiUrl}/logout`);
    req.flush('server error', { status: 500, statusText: 'Server Error' });

    expect(service.accessToken()).toBeNull();
  });

  it('exposes null userId when not logged in', () => {
    expect(service.userId()).toBeNull();
  });

  it('exposes the userId decoded from the access token claims after login', () => {
    const token = makeFakeJwt({ sub: 'jdoe', userId: 42, scope: 'FULL_AUTH' });
    service.login({ username: 'jdoe', password: 'secret123' }).subscribe();
    httpMock.expectOne(`${environment.authApiUrl}/login`).flush({ status: 'SUCCESS', access_token: token });

    expect(service.userId()).toBe(42);
  });

  it('registers a new user without storing an access token', () => {
    let result: unknown;
    service
      .register({ username: 'jdoe', password: 'secret123', phoneNumber: '+15551234567' })
      .subscribe((res) => (result = res));

    const req = httpMock.expectOne(`${environment.authApiUrl}/register`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      username: 'jdoe',
      password: 'secret123',
      phoneNumber: '+15551234567',
    });
    req.flush({ status: 'SUCCESS', message: 'Account created successfully' });

    expect(result).toEqual({ status: 'SUCCESS', message: 'Account created successfully' });
    expect(service.accessToken()).toBeNull();
    expect(service.isLoggedIn()).toBeFalse();
  });
});
