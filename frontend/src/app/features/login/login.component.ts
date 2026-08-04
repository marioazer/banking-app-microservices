import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth.service';
import { InputComponent } from '../../shared/input/input.component';
import { ButtonComponent } from '../../shared/button/button.component';
import { AlertBannerComponent } from '../../shared/alert-banner/alert-banner.component';

type LoginStep = 'credentials' | 'twoFactor';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, RouterLink, InputComponent, ButtonComponent, AlertBannerComponent],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
})
export class LoginComponent {
  readonly step = signal<LoginStep>('credentials');
  readonly username = signal('');
  readonly password = signal('');
  readonly code = signal('');
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly demoCode = signal<string | null>(null);

  constructor(
    private readonly authService: AuthService,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
  ) {
    if (this.route.snapshot.queryParamMap.get('registered') === 'true') {
      this.successMessage.set('Account created successfully. Please log in.');
    }
  }

  onLoginSubmit(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.authService.login({ username: this.username(), password: this.password() }).subscribe({
      next: (response) => {
        this.loading.set(false);
        if (response.status === 'SUCCESS') {
          this.router.navigate(['/dashboard']);
        } else {
          if (response.demoCode) {
            this.demoCode.set(response.demoCode);
            this.code.set(response.demoCode);
          }
          this.step.set('twoFactor');
        }
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('Invalid username or password.');
      },
    });
  }

  onVerifySubmit(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.authService.verifyTwoFa({ code: this.code() }).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/dashboard']);
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('Invalid verification code. Please try again.');
      },
    });
  }
}
