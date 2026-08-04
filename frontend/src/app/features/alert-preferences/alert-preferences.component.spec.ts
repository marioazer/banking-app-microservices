import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AlertPreferencesComponent } from './alert-preferences.component';
import { ProfileService } from '../../core/services/profile.service';
import { AuthService } from '../../core/auth.service';
import { UserPreference } from '../../core/models/profile.models';

describe('AlertPreferencesComponent', () => {
  let fixture: ComponentFixture<AlertPreferencesComponent>;
  let profileServiceSpy: jasmine.SpyObj<ProfileService>;

  const mockPreference: UserPreference = {
    userId: 42,
    alertThresholdAmount: 500,
    dailySummaryEnabled: true,
    timezone: 'America/New_York',
  };

  function setup(pref: UserPreference = mockPreference): void {
    profileServiceSpy = jasmine.createSpyObj('ProfileService', [
      'getPreferences',
      'updateAlertThreshold',
      'updateDailySummary',
    ]);
    profileServiceSpy.getPreferences.and.returnValue(of(pref));
    const authServiceSpy = jasmine.createSpyObj('AuthService', ['logout'], { userId: () => 42 });
    authServiceSpy.logout.and.returnValue(of({}));

    TestBed.configureTestingModule({
      imports: [AlertPreferencesComponent],
      providers: [
        provideRouter([]),
        { provide: ProfileService, useValue: profileServiceSpy },
        { provide: AuthService, useValue: authServiceSpy },
      ],
    });

    fixture = TestBed.createComponent(AlertPreferencesComponent);
    fixture.detectChanges();
  }

  function thresholdInput(): HTMLInputElement {
    return fixture.nativeElement.querySelector('input[name="threshold"]');
  }

  function dailySummaryToggle(): HTMLInputElement {
    return fixture.nativeElement.querySelector('input[name="dailySummaryEnabled"]');
  }

  function timezoneSelect(): HTMLSelectElement {
    return fixture.nativeElement.querySelector('select[name="timezone"]');
  }

  function clickButtonContaining(text: string): void {
    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    buttons.find((b) => b.textContent?.includes(text))!.click();
  }

  it('loads and pre-fills existing preferences on init', () => {
    setup();
    expect(profileServiceSpy.getPreferences).toHaveBeenCalledWith(42);
    expect(thresholdInput().value).toBe('500');
    expect(dailySummaryToggle().checked).toBeTrue();
    expect(timezoneSelect().value).toBe('America/New_York');
  });

  it('saves a valid threshold and shows a confirmation', () => {
    setup();
    profileServiceSpy.updateAlertThreshold.and.returnValue(of({}));

    thresholdInput().value = '750';
    thresholdInput().dispatchEvent(new Event('input'));
    clickButtonContaining('Save Threshold');
    fixture.detectChanges();

    expect(profileServiceSpy.updateAlertThreshold).toHaveBeenCalledWith(750);
    expect(fixture.nativeElement.textContent).toContain('saved');
  });

  it('shows inline validation and does not save a non-positive threshold', () => {
    setup();
    thresholdInput().value = '0';
    thresholdInput().dispatchEvent(new Event('input'));
    clickButtonContaining('Save Threshold');
    fixture.detectChanges();

    expect(profileServiceSpy.updateAlertThreshold).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('positive amount');
  });

  it('saves daily summary settings and shows a confirmation', () => {
    setup();
    profileServiceSpy.updateDailySummary.and.returnValue(of({}));

    timezoneSelect().value = 'America/Chicago';
    timezoneSelect().dispatchEvent(new Event('change'));
    clickButtonContaining('Save Alerts');
    fixture.detectChanges();

    expect(profileServiceSpy.updateDailySummary).toHaveBeenCalledWith(true, 'America/Chicago');
    expect(fixture.nativeElement.textContent).toContain('saved');
  });

  it('does not require a timezone when daily summary is turned off', () => {
    setup({ ...mockPreference, dailySummaryEnabled: false, timezone: '' });
    profileServiceSpy.updateDailySummary.and.returnValue(of({}));

    clickButtonContaining('Save Alerts');
    fixture.detectChanges();

    expect(profileServiceSpy.updateDailySummary).toHaveBeenCalledWith(false, '');
  });

  it('shows an error message when saving the threshold fails', () => {
    setup();
    profileServiceSpy.updateAlertThreshold.and.returnValue(throwError(() => new Error('server error')));

    thresholdInput().value = '750';
    thresholdInput().dispatchEvent(new Event('input'));
    clickButtonContaining('Save Threshold');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Something went wrong');
  });
});
