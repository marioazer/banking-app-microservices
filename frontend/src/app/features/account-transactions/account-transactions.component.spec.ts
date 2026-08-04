import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AccountTransactionsComponent } from './account-transactions.component';
import { AccountService } from '../../core/services/account.service';
import { TransactionPage } from '../../core/models/account.models';
import { AuthService } from '../../core/auth.service';

describe('AccountTransactionsComponent', () => {
  let fixture: ComponentFixture<AccountTransactionsComponent>;
  let accountServiceSpy: jasmine.SpyObj<AccountService>;

  const onePage: TransactionPage = {
    content: [
      { id: 1, accountId: 1, transactionType: 'DEBIT', amount: 4.5, description: 'Coffee Shop', createdAt: '2026-08-01T10:00:00Z' },
      { id: 2, accountId: 1, transactionType: 'CREDIT', amount: 2000, description: 'Payroll', createdAt: '2026-07-31T09:00:00Z' },
    ],
    totalPages: 1,
    totalElements: 2,
    number: 0,
    size: 50,
  };

  function setup(page: TransactionPage = onePage): void {
    accountServiceSpy = jasmine.createSpyObj('AccountService', ['getTransactions']);
    accountServiceSpy.getTransactions.and.returnValue(of(page));

    TestBed.configureTestingModule({
      imports: [AccountTransactionsComponent],
      providers: [
        provideRouter([]),
        { provide: AccountService, useValue: accountServiceSpy },
        { provide: AuthService, useValue: jasmine.createSpyObj('AuthService', { logout: of({}) }) },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: (key: string) => (key === 'id' ? '1' : null) } } },
        },
      ],
    });

    fixture = TestBed.createComponent(AccountTransactionsComponent);
    fixture.detectChanges();
  }

  it('loads transactions for the account id from the route on init', () => {
    setup();
    expect(accountServiceSpy.getTransactions).toHaveBeenCalledWith(1, { page: 0 });
  });

  it('renders each transaction with its date, type, description, and amount', () => {
    setup();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Coffee Shop');
    expect(text).toContain('DEBIT');
    expect(text).toContain('4.5');
    expect(text).toContain('Payroll');
    expect(text).toContain('CREDIT');
  });

  it('shows an empty state when there are no transactions', () => {
    setup({ content: [], totalPages: 0, totalElements: 0, number: 0, size: 50 });
    expect(fixture.nativeElement.textContent).toContain('No transactions');
  });

  it('re-fetches with the type filter when the user filters by type', () => {
    setup();
    accountServiceSpy.getTransactions.calls.reset();

    const select: HTMLSelectElement = fixture.nativeElement.querySelector('select');
    select.value = 'CREDIT';
    select.dispatchEvent(new Event('change'));

    expect(accountServiceSpy.getTransactions).toHaveBeenCalledWith(1, { type: 'CREDIT', page: 0 });
  });

  it('shows pagination controls and re-fetches the next page on Next click', () => {
    setup({ ...onePage, totalPages: 4, number: 0 });
    expect(fixture.nativeElement.textContent).toContain('Page 1 of 4');

    accountServiceSpy.getTransactions.calls.reset();
    accountServiceSpy.getTransactions.and.returnValue(of({ ...onePage, totalPages: 4, number: 1 }));

    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    const nextButton = buttons.find((b) => b.textContent?.includes('Next'))!;
    nextButton.click();

    expect(accountServiceSpy.getTransactions).toHaveBeenCalledWith(1, { page: 1 });
  });
});
