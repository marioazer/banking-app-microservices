import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';

import { jwtInterceptor } from './jwt.interceptor';
import { AuthService } from './auth.service';
import { environment } from '../../environments/environment';

describe('jwtInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authService: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([jwtInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('attaches the Authorization header when an access token is present', () => {
    authService.login({ username: 'jdoe', password: 'secret123' }).subscribe();
    httpMock.expectOne(`${environment.authApiUrl}/login`).flush({ status: 'SUCCESS', access_token: 'token-abc' });

    http.get(`${environment.accountApiUrl}`).subscribe();
    const req = httpMock.expectOne(`${environment.accountApiUrl}`);
    expect(req.request.headers.get('Authorization')).toBe('Bearer token-abc');
    req.flush([]);
  });

  it('does not attach an Authorization header when there is no access token', () => {
    http.get(`${environment.accountApiUrl}`).subscribe();
    const req = httpMock.expectOne(`${environment.accountApiUrl}`);
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush([]);
  });

  it('on a 401, silently refreshes the token and retries the original request once', () => {
    authService.login({ username: 'jdoe', password: 'secret123' }).subscribe();
    httpMock.expectOne(`${environment.authApiUrl}/login`).flush({ status: 'SUCCESS', access_token: 'expired-token' });

    let result: unknown;
    http.get(`${environment.accountApiUrl}`).subscribe((res) => (result = res));

    const firstAttempt = httpMock.expectOne(`${environment.accountApiUrl}`);
    expect(firstAttempt.request.headers.get('Authorization')).toBe('Bearer expired-token');
    firstAttempt.flush('unauthorized', { status: 401, statusText: 'Unauthorized' });

    const refreshReq = httpMock.expectOne(`${environment.authApiUrl}/refresh`);
    refreshReq.flush({ access_token: 'new-token' });

    const retryAttempt = httpMock.expectOne(`${environment.accountApiUrl}`);
    expect(retryAttempt.request.headers.get('Authorization')).toBe('Bearer new-token');
    retryAttempt.flush(['ok']);

    expect(result).toEqual(['ok']);
  });

  it('propagates the error if refresh also fails after a 401', () => {
    authService.login({ username: 'jdoe', password: 'secret123' }).subscribe();
    httpMock.expectOne(`${environment.authApiUrl}/login`).flush({ status: 'SUCCESS', access_token: 'expired-token' });

    let errored = false;
    http.get(`${environment.accountApiUrl}`).subscribe({ error: () => (errored = true) });

    httpMock
      .expectOne(`${environment.accountApiUrl}`)
      .flush('unauthorized', { status: 401, statusText: 'Unauthorized' });
    httpMock
      .expectOne(`${environment.authApiUrl}/refresh`)
      .flush('refresh failed', { status: 401, statusText: 'Unauthorized' });

    expect(errored).toBeTrue();
    expect(authService.accessToken()).toBeNull();
  });
});
