import { Component, Input, forwardRef, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

@Component({
  selector: 'app-input',
  standalone: true,
  template: `
    <div class="input-group">
      <label [for]="id">{{ label }}</label>
      <input
        [id]="id"
        [type]="type"
        [placeholder]="placeholder"
        [disabled]="disabled()"
        [value]="value()"
        (input)="onInput($event)"
        (blur)="onTouched()"
      />
      @if (errorMessage) {
        <span class="input-error">{{ errorMessage }}</span>
      }
    </div>
  `,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => InputComponent),
      multi: true,
    },
  ],
})
export class InputComponent implements ControlValueAccessor {
  @Input() label = '';
  @Input() type = 'text';
  @Input() placeholder = '';
  @Input() id = '';
  @Input() errorMessage: string | null = null;

  readonly value = signal('');
  readonly disabled = signal(false);

  private onChange: (value: string) => void = () => {};
  onTouched: () => void = () => {};

  writeValue(value: string): void {
    this.value.set(value ?? '');
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }

  onInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.value.set(value);
    this.onChange(value);
  }
}
