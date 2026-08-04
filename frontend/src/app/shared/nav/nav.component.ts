import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Router } from '@angular/router';

import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-nav',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './nav.component.html',
})
export class NavComponent {
  constructor(
    private readonly authService: AuthService,
    private readonly router: Router,
  ) {}

  logout(): void {
    this.authService.logout().subscribe({
      next: () => this.router.navigate(['/login']),
      error: () => this.router.navigate(['/login']),
    });
  }
}
