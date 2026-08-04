import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ProfileService } from '../../core/services/profile.service';
import { AuthService } from '../../core/auth.service';
import { ButtonComponent } from '../../shared/button/button.component';
import { AlertBannerComponent } from '../../shared/alert-banner/alert-banner.component';
import { NavComponent } from '../../shared/nav/nav.component';
import { InputComponent } from '../../shared/input/input.component';

@Component({
  selector: 'app-alert-preferences',
  standalone: true,
  imports: [FormsModule, ButtonComponent, AlertBannerComponent, NavComponent, InputComponent],
  templateUrl: './alert-preferences.component.html',
  styleUrl: './alert-preferences.component.css',
})
export class AlertPreferencesComponent implements OnInit {
  readonly threshold = signal('');
  readonly dailySummaryEnabled = signal(false);
  readonly timezone = signal('');

  readonly thresholdError = signal<string | null>(null);
  readonly thresholdMessage = signal<string | null>(null);
  readonly thresholdMessageType = signal<'success' | 'error'>('success');

  readonly dailySummaryMessage = signal<string | null>(null);
  readonly dailySummaryMessageType = signal<'success' | 'error'>('success');

  private userId: number | null = null;

  constructor(
    private readonly profileService: ProfileService,
    private readonly authService: AuthService,
  ) {}

  ngOnInit(): void {
    this.userId = this.authService.userId();
    if (this.userId !== null) {
      this.profileService.getPreferences(this.userId).subscribe((pref) => {
        this.threshold.set(String(pref.alertThresholdAmount));
        this.dailySummaryEnabled.set(pref.dailySummaryEnabled);
        this.timezone.set(pref.timezone);
      });
    }
  }

  saveThreshold(): void {
    this.thresholdError.set(null);
    this.thresholdMessage.set(null);

    const amount = Number(this.threshold());
    if (!amount || amount <= 0) {
      this.thresholdError.set('Please enter a positive amount.');
      return;
    }

    this.profileService.updateAlertThreshold(amount).subscribe({
      next: () => {
        this.thresholdMessageType.set('success');
        this.thresholdMessage.set('Alert threshold saved.');
      },
      error: () => {
        this.thresholdMessageType.set('error');
        this.thresholdMessage.set('Something went wrong. Please try again.');
      },
    });
  }

  saveDailySummary(): void {
    this.dailySummaryMessage.set(null);

    this.profileService.updateDailySummary(this.dailySummaryEnabled(), this.timezone()).subscribe({
      next: () => {
        this.dailySummaryMessageType.set('success');
        this.dailySummaryMessage.set('Alert preferences saved.');
      },
      error: () => {
        this.dailySummaryMessageType.set('error');
        this.dailySummaryMessage.set('Something went wrong. Please try again.');
      },
    });
  }
}
