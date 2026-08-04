import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';

import { ProfileService } from './profile.service';
import { environment } from '../../../environments/environment';
import { KycStatus, UserPreference } from '../models/profile.models';

describe('ProfileService', () => {
  let service: ProfileService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ProfileService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ProfileService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('sends a PUT request to update contact info', () => {
    const request = {
      phoneNumber: '+15551234567',
      addressLine1: '123 Main St',
      city: 'Springfield',
      state: 'IL',
      zipCode: '62704',
    };

    service.updateContactInfo(request).subscribe();

    const req = httpMock.expectOne(`${environment.profileApiUrl}/profiles/me/contact-info`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(request);
    req.flush({});
  });

  it('fetches the KYC status for a given user id', () => {
    let result: KycStatus | undefined;

    service.getKycStatus(42).subscribe((status) => (result = status));

    const req = httpMock.expectOne(`${environment.profileApiUrl}/profiles/42/kyc-status`);
    expect(req.request.method).toBe('GET');
    req.flush({ status: 'APPROVED' });

    expect(result).toBe('APPROVED');
  });

  it('fetches alert preferences for a given user id', () => {
    const mockPreference: UserPreference = {
      userId: 42,
      alertThresholdAmount: 500,
      dailySummaryEnabled: true,
      timezone: 'America/New_York',
    };
    let result: UserPreference | undefined;

    service.getPreferences(42).subscribe((pref) => (result = pref));

    const req = httpMock.expectOne(`${environment.profileApiUrl}/profile/alerts/42`);
    expect(req.request.method).toBe('GET');
    req.flush(mockPreference);

    expect(result).toEqual(mockPreference);
  });

  it('sends a PUT request to update the alert threshold, expecting a plain-text response (backend returns text/plain, not JSON)', () => {
    service.updateAlertThreshold(500).subscribe();

    const req = httpMock.expectOne(`${environment.profileApiUrl}/profile/alerts/threshold`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ alertThresholdAmount: 500 });
    expect(req.request.responseType).toBe('text');
    req.flush('Alert threshold preferences successfully updated.');
  });

  it('sends a PUT request to update daily summary settings, expecting a plain-text response (backend returns text/plain, not JSON)', () => {
    service.updateDailySummary(true, 'America/New_York').subscribe();

    const req = httpMock.expectOne(`${environment.profileApiUrl}/profile/alerts/daily-summary`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ dailySummaryEnabled: true, timezone: 'America/New_York' });
    expect(req.request.responseType).toBe('text');
    req.flush('Daily summary preferences successfully updated.');
  });
});
