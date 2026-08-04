import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, signal } from '@angular/core';

import { ButtonComponent } from './button.component';

@Component({
  selector: 'app-button-host',
  standalone: true,
  imports: [ButtonComponent],
  template: `<app-button [variant]="variant" [disabled]="disabled()" (clicked)="onClick()">Save</app-button>`,
})
class HostComponent {
  variant: 'primary' | 'secondary' | 'danger' = 'primary';
  disabled = signal(false);
  clickCount = 0;
  onClick(): void {
    this.clickCount++;
  }
}

describe('ButtonComponent', () => {
  let fixture: ComponentFixture<HostComponent>;
  let host: HostComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HostComponent] });
    fixture = TestBed.createComponent(HostComponent);
    host = fixture.componentInstance;
    fixture.detectChanges();
  });

  function buttonEl(): HTMLButtonElement {
    return fixture.nativeElement.querySelector('button');
  }

  it('renders projected content', () => {
    expect(buttonEl().textContent).toContain('Save');
  });

  it('applies a css class for the given variant', () => {
    expect(buttonEl().classList).toContain('btn-primary');
  });

  it('emits clicked when clicked', () => {
    buttonEl().click();
    expect(host.clickCount).toBe(1);
  });

  it('is disabled and does not emit clicked when disabled is true', () => {
    host.disabled.set(true);
    fixture.detectChanges();

    expect(buttonEl().disabled).toBeTrue();
    buttonEl().click();
    expect(host.clickCount).toBe(0);
  });
});
