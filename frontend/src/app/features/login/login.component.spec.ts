import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';

import { LoginComponent } from './login.component';
import { AuthService } from '../../core/auth.service';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let router: Router;

  function setup(queryParams: Record<string, string> = {}): void {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['login', 'verifyTwoFa']);

    TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceSpy },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } },
        },
      ],
    });

    fixture = TestBed.createComponent(LoginComponent);
    router = TestBed.inject(Router);
    spyOn(router, 'navigate');
    fixture.detectChanges();
  }

  beforeEach(async () => {
    setup();
    await fixture.whenStable();
  });

  function usernameInput(): HTMLInputElement {
    return fixture.nativeElement.querySelector('app-input[id="username"] input');
  }

  function passwordInput(): HTMLInputElement {
    return fixture.nativeElement.querySelector('app-input[id="password"] input');
  }

  function codeInput(): HTMLInputElement | null {
    return fixture.nativeElement.querySelector('app-input[id="code"] input');
  }

  function submitForm(): void {
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
  }

  function typeInto(input: HTMLInputElement, value: string): void {
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  it('renders the credentials form and no 2FA code input initially', () => {
    expect(usernameInput()).not.toBeNull();
    expect(passwordInput()).not.toBeNull();
    expect(codeInput()).toBeNull();
  });

  it('calls AuthService.login with the entered credentials on submit', () => {
    authServiceSpy.login.and.returnValue(of({ status: 'SUCCESS', access_token: 'token-abc' }));

    typeInto(usernameInput(), 'jdoe');
    typeInto(passwordInput(), 'secret123');
    submitForm();

    expect(authServiceSpy.login).toHaveBeenCalledWith({ username: 'jdoe', password: 'secret123' });
  });

  it('navigates to /dashboard when login succeeds', () => {
    authServiceSpy.login.and.returnValue(of({ status: 'SUCCESS', access_token: 'token-abc' }));

    typeInto(usernameInput(), 'jdoe');
    typeInto(passwordInput(), 'secret123');
    submitForm();

    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('shows the 2FA code step when login responds with 2FA_REQUIRED', () => {
    authServiceSpy.login.and.returnValue(of({ status: '2FA_REQUIRED', pre_auth_token: 'pre-auth-xyz' }));

    typeInto(usernameInput(), 'jdoe');
    typeInto(passwordInput(), 'secret123');
    submitForm();
    fixture.detectChanges();

    expect(codeInput()).not.toBeNull();
    expect(usernameInput()).toBeNull();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('shows an error message when login fails', () => {
    authServiceSpy.login.and.returnValue(throwError(() => new Error('unauthorized')));

    typeInto(usernameInput(), 'jdoe');
    typeInto(passwordInput(), 'wrong');
    submitForm();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Invalid username or password');
  });

  describe('2FA verification step', () => {
    beforeEach(async () => {
      authServiceSpy.login.and.returnValue(of({ status: '2FA_REQUIRED', pre_auth_token: 'pre-auth-xyz' }));
      typeInto(usernameInput(), 'jdoe');
      typeInto(passwordInput(), 'secret123');
      submitForm();
      fixture.detectChanges();
      await fixture.whenStable();
    });

    it('calls AuthService.verifyTwoFa with the entered code on submit', () => {
      authServiceSpy.verifyTwoFa.and.returnValue(of({ status: 'SUCCESS', access_token: 'full-token' }));

      typeInto(codeInput()!, '123456');
      submitForm();

      expect(authServiceSpy.verifyTwoFa).toHaveBeenCalledWith({ code: '123456' });
    });

    it('navigates to /dashboard when 2FA verification succeeds', () => {
      authServiceSpy.verifyTwoFa.and.returnValue(of({ status: 'SUCCESS', access_token: 'full-token' }));

      typeInto(codeInput()!, '123456');
      submitForm();

      expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
    });

    it('shows an error message when 2FA verification fails', () => {
      authServiceSpy.verifyTwoFa.and.returnValue(throwError(() => new Error('invalid code')));

      typeInto(codeInput()!, '000000');
      submitForm();
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('Invalid verification code');
    });
  });

  it('renders a link to /signup', () => {
    const links: HTMLAnchorElement[] = Array.from(fixture.nativeElement.querySelectorAll('a'));
    expect(links.some((a) => a.getAttribute('href') === '/signup')).toBeTrue();
  });

  describe('after registering (redirected with ?registered=true)', () => {
    beforeEach(async () => {
      TestBed.resetTestingModule();
      setup({ registered: 'true' });
      await fixture.whenStable();
    });

    it('shows a success banner prompting the user to log in', () => {
      expect(fixture.nativeElement.textContent).toContain('Account created');
    });
  });
});
