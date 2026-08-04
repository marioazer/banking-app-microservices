import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'app-modal',
  standalone: true,
  template: `
    @if (open) {
      <div class="modal-overlay">
        <div class="modal-dialog">
          <div class="modal-header">
            <h2>{{ title }}</h2>
            <button class="modal-close" type="button" (click)="closed.emit()">&times;</button>
          </div>
          <div class="modal-body">
            <ng-content></ng-content>
          </div>
        </div>
      </div>
    }
  `,
})
export class ModalComponent {
  @Input() open = false;
  @Input() title = '';
  @Output() closed = new EventEmitter<void>();
}
