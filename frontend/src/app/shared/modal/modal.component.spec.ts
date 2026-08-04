import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, signal } from '@angular/core';

import { ModalComponent } from './modal.component';

@Component({
  selector: 'app-modal-host',
  standalone: true,
  imports: [ModalComponent],
  template: `<app-modal [open]="open()" title="Confirm Transfer" (closed)="onClosed()">Body content</app-modal>`,
})
class HostComponent {
  open = signal(false);
  closedCount = 0;
  onClosed(): void {
    this.closedCount++;
  }
}

describe('ModalComponent', () => {
  let fixture: ComponentFixture<HostComponent>;
  let host: HostComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HostComponent] });
    fixture = TestBed.createComponent(HostComponent);
    host = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders nothing when open is false', () => {
    expect(fixture.nativeElement.querySelector('.modal-overlay')).toBeNull();
  });

  it('renders the title and projected content when open is true', () => {
    host.open.set(true);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.modal-overlay')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Confirm Transfer');
    expect(fixture.nativeElement.textContent).toContain('Body content');
  });

  it('emits closed when the close button is clicked', () => {
    host.open.set(true);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('.modal-close').click();
    expect(host.closedCount).toBe(1);
  });
});
