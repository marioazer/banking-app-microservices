import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'app-button',
  standalone: true,
  template: `
    <button
      [type]="type"
      [class]="'btn btn-' + variant"
      [disabled]="disabled"
      (click)="clicked.emit()"
    >
      <ng-content></ng-content>
    </button>
  `,
})
export class ButtonComponent {
  @Input() variant: 'primary' | 'secondary' | 'danger' = 'primary';
  @Input() type: 'button' | 'submit' = 'button';
  @Input() disabled = false;
  @Output() clicked = new EventEmitter<void>();
}
