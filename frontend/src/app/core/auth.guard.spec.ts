import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';

import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';
import { environment } from '../../environments/environment';

describe('authGuard', () => {
  let authService: AuthService;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    authService = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function runGuard(): boolean | UrlTree {
    return TestBed.runInInjectionContext(() => authGuard({} as never, {} as never)) as boolean | UrlTree;
  }

  it('allows navigation when the user is logged in', () => {
    authService.login({ username: 'jdoe', password: 'secret123' }).subscribe();
    httpMock.expectOne(`${environment.authApiUrl}/login`).flush({ status: 'SUCCESS', access_token: 'token-abc' });

    expect(runGuard()).toBeTrue();
  });

  it('redirects to /login when the user is not logged in', () => {
    const result = runGuard();

    expect(result).not.toBeTrue();
    expect((result as UrlTree).toString()).toBe(router.createUrlTree(['/login']).toString());
  });
});
