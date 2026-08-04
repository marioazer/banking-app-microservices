import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ContactInfo, KycStatus, UserPreference } from '../models/profile.models';

@Injectable({ providedIn: 'root' })
export class ProfileService {
  private readonly baseUrl = environment.profileApiUrl;

  constructor(private readonly http: HttpClient) {}

  updateContactInfo(request: ContactInfo): Observable<unknown> {
    return this.http.put(`${this.baseUrl}/profiles/me/contact-info`, request);
  }

  getKycStatus(userId: number): Observable<KycStatus> {
    return this.http
      .get<{ status: KycStatus }>(`${this.baseUrl}/profiles/${userId}/kyc-status`)
      .pipe(map((response) => response.status));
  }

  // Demo-only: simulates the KYC vendor's webhook callback for the logged-in user themselves.
  // 404s if the backend's app.demo.enabled flag is off.
  simulateKycApproval(): Observable<KycStatus> {
    return this.http
      .post<{ status: KycStatus }>(`${this.baseUrl}/profiles/kyc/simulate-approval`, {})
      .pipe(map((response) => response.status));
  }

  getPreferences(userId: number): Observable<UserPreference> {
    return this.http.get<UserPreference>(`${this.baseUrl}/profile/alerts/${userId}`);
  }

  updateAlertThreshold(amount: number): Observable<unknown> {
    return this.http.put(
      `${this.baseUrl}/profile/alerts/threshold`,
      { alertThresholdAmount: amount },
      { responseType: 'text' },
    );
  }

  updateDailySummary(enabled: boolean, timezone: string): Observable<unknown> {
    return this.http.put(
      `${this.baseUrl}/profile/alerts/daily-summary`,
      { dailySummaryEnabled: enabled, timezone },
      { responseType: 'text' },
    );
  }
}
