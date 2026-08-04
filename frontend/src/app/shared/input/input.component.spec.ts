import { ComponentFixture, TestBed } from '@angular/core/testing';

import { InputComponent } from './input.component';

describe('InputComponent', () => {
  let fixture: ComponentFixture<InputComponent>;
  let component: InputComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [InputComponent] });
    fixture = TestBed.createComponent(InputComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('label', 'Username');
    fixture.detectChanges();
  });

  function inputEl(): HTMLInputElement {
    return fixture.nativeElement.querySelector('input');
  }

  it('renders the label', () => {
    expect(fixture.nativeElement.querySelector('label').textContent).toContain('Username');
  });

  it('writeValue sets the displayed input value (ControlValueAccessor contract)', () => {
    component.writeValue('jdoe');
    fixture.detectChanges();
    expect(inputEl().value).toBe('jdoe');
  });

  it('notifies the registered onChange callback when the user types', () => {
    let notified: string | undefined;
    component.registerOnChange((value: string) => (notified = value));

    inputEl().value = 'jdoe';
    inputEl().dispatchEvent(new Event('input'));

    expect(notified).toBe('jdoe');
  });

  it('notifies the registered onTouched callback on blur', () => {
    let touched = false;
    component.registerOnTouched(() => (touched = true));

    inputEl().dispatchEvent(new Event('blur'));

    expect(touched).toBeTrue();
  });

  it('disables the native input when setDisabledState(true) is called', () => {
    component.setDisabledState(true);
    fixture.detectChanges();
    expect(inputEl().disabled).toBeTrue();
  });

  it('shows an error message when provided', () => {
    fixture.componentRef.setInput('errorMessage', 'Username is required');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Username is required');
  });

  it('shows no error text when errorMessage is null', () => {
    expect(fixture.nativeElement.querySelector('.input-error')).toBeNull();
  });
});
