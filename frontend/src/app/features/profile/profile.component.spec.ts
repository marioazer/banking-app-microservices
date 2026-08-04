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

  async function setup(kycStatus: KycStatus = 'PENDING_VERIFICATION'): Promise<void> {
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
    await fixture.whenStable();
  }

  function inputById(id: string): HTMLInputElement {
    return fixture.nativeElement.querySelector(`app-input[id="${id}"] input`);
  }

  function setValue(id: string, value: string): void {
    const input = inputById(id);
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  async function fillValidForm(): Promise<void> {
    setValue('phoneNumber', '+15551234567');
    setValue('addressLine1', '123 Main St');
    setValue('city', 'Springfield');
    setValue('state', 'IL');
    setValue('zipCode', '62704');
    await fixture.whenStable();
  }

  function clickButtonContaining(text: string): void {
    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    buttons.find((b) => b.textContent?.includes(text))!.click();
  }

  it('fetches and displays the KYC status for the logged-in user', async () => {
    await setup('APPROVED');
    expect(profileServiceSpy.getKycStatus).toHaveBeenCalledWith(42);
    expect(fixture.nativeElement.textContent).toContain('APPROVED');
  });

  it('shows an informational note when KYC status is not APPROVED', async () => {
    await setup('PENDING_VERIFICATION');
    expect(fixture.nativeElement.textContent).toContain('verify');
  });

  it('does not show the informational note when KYC status is APPROVED', async () => {
    await setup('APPROVED');
    expect(fixture.nativeElement.textContent.toLowerCase()).not.toContain('verify your identity');
  });

  it('submits valid contact info and shows a save confirmation', async () => {
    await setup();
    profileServiceSpy.updateContactInfo.and.returnValue(of({}));

    await fillValidForm();
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

  it('shows inline validation and does not submit for an invalid phone number', async () => {
    await setup();
    await fillValidForm();
    setValue('phoneNumber', 'not-a-phone');
    await fixture.whenStable();

    clickButtonContaining('Save');
    fixture.detectChanges();

    expect(profileServiceSpy.updateContactInfo).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('phone number');
  });

  it('shows an error message when saving fails', async () => {
    await setup();
    profileServiceSpy.updateContactInfo.and.returnValue(throwError(() => new Error('server error')));

    await fillValidForm();
    clickButtonContaining('Save');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Something went wrong');
  });
});
