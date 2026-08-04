import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ProfileService } from '../../core/services/profile.service';
import { AuthService } from '../../core/auth.service';
import { KycStatus } from '../../core/models/profile.models';
import { ButtonComponent } from '../../shared/button/button.component';
import { AlertBannerComponent } from '../../shared/alert-banner/alert-banner.component';
import { NavComponent } from '../../shared/nav/nav.component';
import { InputComponent } from '../../shared/input/input.component';

const PHONE_PATTERN = /^\+?[1-9]\d{1,14}$/;

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [FormsModule, ButtonComponent, AlertBannerComponent, NavComponent, InputComponent],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.css',
})
export class ProfileComponent implements OnInit {
  readonly kycStatus = signal<KycStatus | null>(null);

  readonly phoneNumber = signal('');
  readonly addressLine1 = signal('');
  readonly addressLine2 = signal('');
  readonly city = signal('');
  readonly state = signal('');
  readonly zipCode = signal('');

  readonly validationError = signal<string | null>(null);
  readonly saveMessage = signal<string | null>(null);
  readonly saveMessageType = signal<'success' | 'error'>('success');

  readonly kycMessage = signal<string | null>(null);
  readonly kycMessageType = signal<'success' | 'error'>('success');

  constructor(
    private readonly profileService: ProfileService,
    private readonly authService: AuthService,
  ) {}

  ngOnInit(): void {
    const userId = this.authService.userId();
    if (userId !== null) {
      this.profileService.getKycStatus(userId).subscribe((status) => this.kycStatus.set(status));
    }
  }

  submit(): void {
    this.validationError.set(null);
    this.saveMessage.set(null);

    if (!PHONE_PATTERN.test(this.phoneNumber())) {
      this.validationError.set('Please enter a valid phone number (e.g. +15551234567).');
      return;
    }
    if (!this.addressLine1() || !this.city() || !this.state() || !this.zipCode()) {
      this.validationError.set('Please fill in all required address fields.');
      return;
    }

    this.profileService
      .updateContactInfo({
        phoneNumber: this.phoneNumber(),
        addressLine1: this.addressLine1(),
        ...(this.addressLine2() ? { addressLine2: this.addressLine2() } : {}),
        city: this.city(),
        state: this.state(),
        zipCode: this.zipCode(),
      })
      .subscribe({
        next: () => {
          this.saveMessageType.set('success');
          this.saveMessage.set('Your contact info has been saved.');
        },
        error: () => {
          this.saveMessageType.set('error');
          this.saveMessage.set('Something went wrong. Please try again.');
        },
      });
  }

  simulateKycApproval(): void {
    this.kycMessage.set(null);

    this.profileService.simulateKycApproval().subscribe({
      next: (status) => {
        this.kycStatus.set(status);
        this.kycMessageType.set('success');
        this.kycMessage.set('KYC approval simulated successfully.');
      },
      error: () => {
        this.kycMessageType.set('error');
        this.kycMessage.set('Could not simulate KYC approval. Please try again.');
      },
    });
  }
}
