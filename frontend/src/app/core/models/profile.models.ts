export interface ContactInfo {
  phoneNumber: string;
  addressLine1: string;
  addressLine2?: string;
  city: string;
  state: string;
  zipCode: string;
}

export type KycStatus = 'PENDING_VERIFICATION' | 'APPROVED' | 'REJECTED';

export interface UserPreference {
  userId: number;
  alertThresholdAmount: number;
  dailySummaryEnabled: boolean;
  timezone: string;
}
