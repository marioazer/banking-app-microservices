import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';

import { AuthService } from '../../core/auth.service';
import { InputComponent } from '../../shared/input/input.component';
import { ButtonComponent } from '../../shared/button/button.component';
import { AlertBannerComponent } from '../../shared/alert-banner/alert-banner.component';

const MIN_PASSWORD_LENGTH = 8;

@Component({
  selector: 'app-signup',
  standalone: true,
  imports: [FormsModule, InputComponent, ButtonComponent, AlertBannerComponent],
  templateUrl: './signup.component.html',
  styleUrl: './signup.component.css',
})
export class SignupComponent {
  readonly username = signal('');
  readonly password = signal('');
  readonly confirmPassword = signal('');
  readonly phoneNumber = signal('');

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  constructor(
    private readonly authService: AuthService,
    private readonly router: Router,
  ) {}

  onSubmit(): void {
    this.errorMessage.set(null);

    if (this.password().length < MIN_PASSWORD_LENGTH) {
      this.errorMessage.set(`Password must be at least ${MIN_PASSWORD_LENGTH} characters.`);
      return;
    }
    if (this.password() !== this.confirmPassword()) {
      this.errorMessage.set('Passwords do not match.');
      return;
    }

    this.loading.set(true);
    this.authService
      .register({
        username: this.username(),
        password: this.password(),
        phoneNumber: this.phoneNumber(),
      })
      .subscribe({
        next: () => {
          this.loading.set(false);
          this.router.navigate(['/login'], { queryParams: { registered: 'true' } });
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.errorMessage.set(this.extractErrorMessage(error));
        },
      });
  }

  private extractErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && typeof error.error?.error === 'string') {
      return error.error.error;
    }
    return 'Something went wrong. Please try again.';
  }
}
