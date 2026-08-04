import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, signal } from '@angular/core';

import { AlertBannerComponent } from './alert-banner.component';

@Component({
  selector: 'app-alert-banner-host',
  standalone: true,
  imports: [AlertBannerComponent],
  template: `<app-alert-banner [type]="type()" [message]="message()" />`,
})
class HostComponent {
  type = signal<'success' | 'error' | 'info' | 'warning'>('info');
  message = signal<string | null>(null);
}

describe('AlertBannerComponent', () => {
  let fixture: ComponentFixture<HostComponent>;
  let host: HostComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HostComponent] });
    fixture = TestBed.createComponent(HostComponent);
    host = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders nothing when message is null', () => {
    expect(fixture.nativeElement.querySelector('.alert')).toBeNull();
  });

  it('renders the message with a css class matching the type', () => {
    host.type.set('error');
    host.message.set('Something went wrong');
    fixture.detectChanges();

    const alert = fixture.nativeElement.querySelector('.alert');
    expect(alert.textContent).toContain('Something went wrong');
    expect(alert.classList).toContain('alert-error');
  });
});
