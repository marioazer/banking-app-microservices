import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-alert-banner',
  standalone: true,
  template: `
    @if (message) {
      <div class="alert" [class]="'alert alert-' + type" role="alert">{{ message }}</div>
    }
  `,
})
export class AlertBannerComponent {
  @Input() type: 'success' | 'error' | 'info' | 'warning' = 'info';
  @Input() message: string | null = null;
}
