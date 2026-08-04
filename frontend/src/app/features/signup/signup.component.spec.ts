import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

import { SignupComponent } from './signup.component';
import { AuthService } from '../../core/auth.service';

describe('SignupComponent', () => {
  let fixture: ComponentFixture<SignupComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let router: Router;

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['register']);

    TestBed.configureTestingModule({
      imports: [SignupComponent],
      providers: [{ provide: AuthService, useValue: authServiceSpy }],
    });

    fixture = TestBed.createComponent(SignupComponent);
    router = TestBed.inject(Router);
    spyOn(router, 'navigate');
    fixture.detectChanges();
  });

  function usernameInput(): HTMLInputElement {
    return fixture.nativeElement.querySelector('app-input[id="username"] input');
  }

  function passwordInput(): HTMLInputElement {
    return fixture.nativeElement.querySelector('app-input[id="password"] input');
  }

  function confirmPasswordInput(): HTMLInputElement {
    return fixture.nativeElement.querySelector('app-input[id="confirmPassword"] input');
  }

  function phoneInput(): HTMLInputElement {
    return fixture.nativeElement.querySelector('app-input[id="phoneNumber"] input');
  }

  function submitForm(): void {
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
  }

  function typeInto(input: HTMLInputElement, value: string): void {
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  async function fillValidFormAndWait(): Promise<void> {
    await fixture.whenStable();
    typeInto(usernameInput(), 'newuser');
    typeInto(passwordInput(), 'SecurePass123!');
    typeInto(confirmPasswordInput(), 'SecurePass123!');
    typeInto(phoneInput(), '+15551234567');
  }

  it('renders the sign-up form', () => {
    expect(usernameInput()).not.toBeNull();
    expect(passwordInput()).not.toBeNull();
    expect(confirmPasswordInput()).not.toBeNull();
    expect(phoneInput()).not.toBeNull();
  });

  it('calls AuthService.register with the entered details on submit', async () => {
    authServiceSpy.register.and.returnValue(of({ status: 'SUCCESS', message: 'Account created successfully' }));
    await fillValidFormAndWait();

    submitForm();

    expect(authServiceSpy.register).toHaveBeenCalledWith({
      username: 'newuser',
      password: 'SecurePass123!',
      phoneNumber: '+15551234567',
    });
  });

  it('navigates to /login with a success message after registering', async () => {
    authServiceSpy.register.and.returnValue(of({ status: 'SUCCESS', message: 'Account created successfully' }));
    await fillValidFormAndWait();

    submitForm();

    expect(router.navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: { registered: 'true' },
    });
  });

  it('shows inline validation and does not submit when passwords do not match', async () => {
    await fillValidFormAndWait();
    typeInto(confirmPasswordInput(), 'DoesNotMatch1!');

    submitForm();
    fixture.detectChanges();

    expect(authServiceSpy.register).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('do not match');
  });

  it('shows inline validation and does not submit for a short password', async () => {
    await fillValidFormAndWait();
    typeInto(passwordInput(), 'short');
    typeInto(confirmPasswordInput(), 'short');

    submitForm();
    fixture.detectChanges();

    expect(authServiceSpy.register).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('8 characters');
  });

  it('shows a server error message when registration fails (e.g. duplicate username)', async () => {
    authServiceSpy.register.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { error: 'Username is already taken' } })),
    );
    await fillValidFormAndWait();

    submitForm();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('already taken');
  });
});
