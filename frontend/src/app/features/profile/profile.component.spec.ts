import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ProfileComponent } from './profile.component';
import { ProfileService } from '../../core/services/profile.service';
import { AuthService } from '../../core/auth.service';
import { KycStatus } from '../../core/models/profile.models';

describe('ProfileComponent', () => {
  let fixture: ComponentFixture<ProfileComponent>;
  let profileServiceSpy: jasmine.SpyObj<ProfileService>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  function setup(kycStatus: KycStatus = 'PENDING_VERIFICATION'): void {
    profileServiceSpy = jasmine.createSpyObj('ProfileService', ['getKycStatus', 'updateContactInfo']);
    profileServiceSpy.getKycStatus.and.returnValue(of(kycStatus));
    authServiceSpy = jasmine.createSpyObj('AuthService', ['logout'], { userId: () => 42 });
    authServiceSpy.logout.and.returnValue(of({}));

    TestBed.configureTestingModule({
      imports: [ProfileComponent],
      providers: [
        provideRouter([]),
        { provide: ProfileService, useValue: profileServiceSpy },
        { provide: AuthService, useValue: authServiceSpy },
      ],
    });

    fixture = TestBed.createComponent(ProfileComponent);
    fixture.detectChanges();
  }

  function inputs(): HTMLInputElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('input'));
  }

  function fillValidForm(): void {
    inputs().find((i) => i.name === 'phoneNumber')!.value = '+15551234567';
    inputs().find((i) => i.name === 'phoneNumber')!.dispatchEvent(new Event('input'));
    inputs().find((i) => i.name === 'addressLine1')!.value = '123 Main St';
    inputs().find((i) => i.name === 'addressLine1')!.dispatchEvent(new Event('input'));
    inputs().find((i) => i.name === 'city')!.value = 'Springfield';
    inputs().find((i) => i.name === 'city')!.dispatchEvent(new Event('input'));
    inputs().find((i) => i.name === 'state')!.value = 'IL';
    inputs().find((i) => i.name === 'state')!.dispatchEvent(new Event('input'));
    inputs().find((i) => i.name === 'zipCode')!.value = '62704';
    inputs().find((i) => i.name === 'zipCode')!.dispatchEvent(new Event('input'));
  }

  function clickButtonContaining(text: string): void {
    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    buttons.find((b) => b.textContent?.includes(text))!.click();
  }

  it('fetches and displays the KYC status for the logged-in user', () => {
    setup('APPROVED');
    expect(profileServiceSpy.getKycStatus).toHaveBeenCalledWith(42);
    expect(fixture.nativeElement.textContent).toContain('APPROVED');
  });

  it('shows an informational note when KYC status is not APPROVED', () => {
    setup('PENDING_VERIFICATION');
    expect(fixture.nativeElement.textContent).toContain('verify');
  });

  it('does not show the informational note when KYC status is APPROVED', () => {
    setup('APPROVED');
    expect(fixture.nativeElement.textContent.toLowerCase()).not.toContain('verify your identity');
  });

  it('submits valid contact info and shows a save confirmation', () => {
    setup();
    profileServiceSpy.updateContactInfo.and.returnValue(of({}));

    fillValidForm();
    clickButtonContaining('Save');
    fixture.detectChanges();

    expect(profileServiceSpy.updateContactInfo).toHaveBeenCalledWith({
      phoneNumber: '+15551234567',
      addressLine1: '123 Main St',
      city: 'Springfield',
      state: 'IL',
      zipCode: '62704',
    });
    expect(fixture.nativeElement.textContent).toContain('saved');
  });

  it('shows inline validation and does not submit for an invalid phone number', () => {
    setup();
    fillValidForm();
    const phoneInput = inputs().find((i) => i.name === 'phoneNumber')!;
    phoneInput.value = 'not-a-phone';
    phoneInput.dispatchEvent(new Event('input'));

    clickButtonContaining('Save');
    fixture.detectChanges();

    expect(profileServiceSpy.updateContactInfo).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('phone number');
  });

  it('shows an error message when saving fails', () => {
    setup();
    profileServiceSpy.updateContactInfo.and.returnValue(throwError(() => new Error('server error')));

    fillValidForm();
    clickButtonContaining('Save');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Something went wrong');
  });
});
