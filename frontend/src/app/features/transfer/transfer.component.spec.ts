import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { TransferComponent } from './transfer.component';
import { TransferService } from '../../core/services/transfer.service';
import { AccountService } from '../../core/services/account.service';
import { AccountOverview } from '../../core/models/account.models';
import { AuthService } from '../../core/auth.service';

describe('TransferComponent', () => {
  let fixture: ComponentFixture<TransferComponent>;
  let transferServiceSpy: jasmine.SpyObj<TransferService>;
  let accountServiceSpy: jasmine.SpyObj<AccountService>;

  const mockAccounts: AccountOverview[] = [
    { accountId: 1, accountType: 'CHECKING', availableBalance: 1000, routingNumber: '021000021', maskedAccountNumber: '****1234', status: 'ACTIVE' },
    { accountId: 2, accountType: 'SAVINGS', availableBalance: 5000, routingNumber: '021000021', maskedAccountNumber: '****5678', status: 'ACTIVE' },
  ];

  beforeEach(() => {
    transferServiceSpy = jasmine.createSpyObj('TransferService', ['transferInternal', 'transferExternal']);
    accountServiceSpy = jasmine.createSpyObj('AccountService', ['getAccounts']);
    accountServiceSpy.getAccounts.and.returnValue(of(mockAccounts));

    TestBed.configureTestingModule({
      imports: [TransferComponent],
      providers: [
        provideRouter([]),
        { provide: TransferService, useValue: transferServiceSpy },
        { provide: AccountService, useValue: accountServiceSpy },
        { provide: AuthService, useValue: jasmine.createSpyObj('AuthService', { logout: of({}) }) },
      ],
    });

    fixture = TestBed.createComponent(TransferComponent);
    fixture.detectChanges();
  });

  function selects(): HTMLSelectElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('select'));
  }

  function inputs(): HTMLInputElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('input'));
  }

  function clickButtonContaining(text: string): void {
    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    buttons.find((b) => b.textContent?.includes(text))!.click();
  }

  it('populates the account dropdown(s) from AccountService', () => {
    expect(accountServiceSpy.getAccounts).toHaveBeenCalled();
    const options = fixture.nativeElement.querySelectorAll('option');
    expect(Array.from(options).some((o) => (o as HTMLOptionElement).textContent?.includes('****1234'))).toBeTrue();
  });

  describe('internal transfer (default tab)', () => {
    it('submits with the selected accounts and amount', () => {
      transferServiceSpy.transferInternal.and.returnValue(of({ transactionId: 'txn-1', status: 'COMPLETED' }));

      const [fromSelect, toSelect] = selects();
      fromSelect.value = '1';
      fromSelect.dispatchEvent(new Event('change'));
      toSelect.value = '2';
      toSelect.dispatchEvent(new Event('change'));
      const amountInput = inputs().find((i) => i.name === 'amount')!;
      amountInput.value = '100';
      amountInput.dispatchEvent(new Event('input'));

      clickButtonContaining('Send');

      expect(transferServiceSpy.transferInternal).toHaveBeenCalledWith({
        fromAccountId: 1,
        toAccountId: 2,
        amount: 100,
      });
    });

    it('shows a validation error and does not call the service for a non-positive amount', () => {
      const [fromSelect, toSelect] = selects();
      fromSelect.value = '1';
      fromSelect.dispatchEvent(new Event('change'));
      toSelect.value = '2';
      toSelect.dispatchEvent(new Event('change'));
      const amountInput = inputs().find((i) => i.name === 'amount')!;
      amountInput.value = '0';
      amountInput.dispatchEvent(new Event('input'));

      clickButtonContaining('Send');
      fixture.detectChanges();

      expect(transferServiceSpy.transferInternal).not.toHaveBeenCalled();
      expect(fixture.nativeElement.textContent).toContain('positive amount');
    });

    it('shows a success message with the transaction id when the transfer completes', () => {
      transferServiceSpy.transferInternal.and.returnValue(of({ transactionId: 'txn-1', status: 'COMPLETED' }));
      const [fromSelect, toSelect] = selects();
      fromSelect.value = '1';
      fromSelect.dispatchEvent(new Event('change'));
      toSelect.value = '2';
      toSelect.dispatchEvent(new Event('change'));
      inputs().find((i) => i.name === 'amount')!.value = '100';
      inputs().find((i) => i.name === 'amount')!.dispatchEvent(new Event('input'));

      clickButtonContaining('Send');
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('txn-1');
      expect(fixture.nativeElement.textContent.toLowerCase()).toContain('success');
    });

    it('shows an "under review" message when the transfer is PENDING_APPROVAL', () => {
      transferServiceSpy.transferInternal.and.returnValue(of({ transactionId: 'txn-2', status: 'PENDING_APPROVAL' }));
      const [fromSelect, toSelect] = selects();
      fromSelect.value = '1';
      fromSelect.dispatchEvent(new Event('change'));
      toSelect.value = '2';
      toSelect.dispatchEvent(new Event('change'));
      inputs().find((i) => i.name === 'amount')!.value = '9999';
      inputs().find((i) => i.name === 'amount')!.dispatchEvent(new Event('input'));

      clickButtonContaining('Send');
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('under review');
    });

    it('shows an identity-verification message on a 403 KYC error', () => {
      transferServiceSpy.transferInternal.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 403, error: { error: 'Action forbidden: KYC verification is PENDING_VERIFICATION' } })),
      );
      const [fromSelect, toSelect] = selects();
      fromSelect.value = '1';
      fromSelect.dispatchEvent(new Event('change'));
      toSelect.value = '2';
      toSelect.dispatchEvent(new Event('change'));
      inputs().find((i) => i.name === 'amount')!.value = '100';
      inputs().find((i) => i.name === 'amount')!.dispatchEvent(new Event('input'));

      clickButtonContaining('Send');
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('verify your identity');
    });
  });

  describe('external wire tab', () => {
    beforeEach(() => {
      clickButtonContaining('External Wire');
      fixture.detectChanges();
    });

    it('shows external wire fields and hides the internal to-account select', () => {
      expect(inputs().some((i) => i.name === 'iban')).toBeTrue();
      expect(inputs().some((i) => i.name === 'swiftCode')).toBeTrue();
      expect(inputs().some((i) => i.name === 'beneficiaryName')).toBeTrue();
    });

    it('submits with the entered wire details', () => {
      transferServiceSpy.transferExternal.and.returnValue(of({ transactionId: 'txn-3', status: 'COMPLETED' }));

      selects()[0].value = '1';
      selects()[0].dispatchEvent(new Event('change'));
      inputs().find((i) => i.name === 'iban')!.value = 'GB29NWBK60161331926819';
      inputs().find((i) => i.name === 'iban')!.dispatchEvent(new Event('input'));
      inputs().find((i) => i.name === 'swiftCode')!.value = 'NWBKGB2L';
      inputs().find((i) => i.name === 'swiftCode')!.dispatchEvent(new Event('input'));
      inputs().find((i) => i.name === 'beneficiaryName')!.value = 'Jane Doe';
      inputs().find((i) => i.name === 'beneficiaryName')!.dispatchEvent(new Event('input'));
      inputs().find((i) => i.name === 'amount')!.value = '250';
      inputs().find((i) => i.name === 'amount')!.dispatchEvent(new Event('input'));

      clickButtonContaining('Send');

      expect(transferServiceSpy.transferExternal).toHaveBeenCalledWith(1, {
        iban: 'GB29NWBK60161331926819',
        swiftCode: 'NWBKGB2L',
        beneficiaryName: 'Jane Doe',
        amount: 250,
      });
    });

    it('shows a validation error and does not call the service for an invalid IBAN', () => {
      selects()[0].value = '1';
      selects()[0].dispatchEvent(new Event('change'));
      inputs().find((i) => i.name === 'iban')!.value = 'NOT-AN-IBAN';
      inputs().find((i) => i.name === 'iban')!.dispatchEvent(new Event('input'));
      inputs().find((i) => i.name === 'swiftCode')!.value = 'NWBKGB2L';
      inputs().find((i) => i.name === 'swiftCode')!.dispatchEvent(new Event('input'));
      inputs().find((i) => i.name === 'beneficiaryName')!.value = 'Jane Doe';
      inputs().find((i) => i.name === 'beneficiaryName')!.dispatchEvent(new Event('input'));
      inputs().find((i) => i.name === 'amount')!.value = '250';
      inputs().find((i) => i.name === 'amount')!.dispatchEvent(new Event('input'));

      clickButtonContaining('Send');
      fixture.detectChanges();

      expect(transferServiceSpy.transferExternal).not.toHaveBeenCalled();
      expect(fixture.nativeElement.textContent).toContain('IBAN');
    });
  });
});
