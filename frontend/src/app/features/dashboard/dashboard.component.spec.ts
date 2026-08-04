import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { DashboardComponent } from './dashboard.component';
import { AccountService } from '../../core/services/account.service';
import { AccountOverview } from '../../core/models/account.models';
import { AuthService } from '../../core/auth.service';

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let accountServiceSpy: jasmine.SpyObj<AccountService>;
  let router: Router;

  const mockAccounts: AccountOverview[] = [
    {
      accountId: 1,
      accountType: 'CHECKING',
      availableBalance: 1204.55,
      routingNumber: '021000021',
      maskedAccountNumber: '****1234',
      status: 'ACTIVE',
    },
    {
      accountId: 2,
      accountType: 'SAVINGS',
      availableBalance: 9003.1,
      routingNumber: '021000021',
      maskedAccountNumber: '****5678',
      status: 'FROZEN',
    },
  ];

  beforeEach(() => {
    accountServiceSpy = jasmine.createSpyObj('AccountService', ['getAccounts']);

    TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        provideRouter([]),
        { provide: AccountService, useValue: accountServiceSpy },
        {
          provide: AuthService,
          useValue: jasmine.createSpyObj('AuthService', { logout: of({}) }),
        },
      ],
    });

    fixture = TestBed.createComponent(DashboardComponent);
    router = TestBed.inject(Router);
    spyOn(router, 'navigate');
  });

  it('calls AccountService.getAccounts on init', () => {
    accountServiceSpy.getAccounts.and.returnValue(of(mockAccounts));
    fixture.detectChanges();
    expect(accountServiceSpy.getAccounts).toHaveBeenCalled();
  });

  it('renders each account with its type, masked number, status, and balance', () => {
    accountServiceSpy.getAccounts.and.returnValue(of(mockAccounts));
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('CHECKING');
    expect(text).toContain('****1234');
    expect(text).toContain('ACTIVE');
    expect(text).toContain('1204.55');
    expect(text).toContain('SAVINGS');
    expect(text).toContain('FROZEN');
  });

  it('navigates to the account transactions page when a row is clicked', () => {
    accountServiceSpy.getAccounts.and.returnValue(of(mockAccounts));
    fixture.detectChanges();

    const firstRow = fixture.nativeElement.querySelector('tbody tr');
    firstRow.click();

    expect(router.navigate).toHaveBeenCalledWith(['/accounts', 1, 'transactions']);
  });

  it('shows an error state with a retry option when the accounts request fails', () => {
    accountServiceSpy.getAccounts.and.returnValue(throwError(() => new Error('network error')));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Unable to load your accounts');
    expect(fixture.nativeElement.querySelector('button')).not.toBeNull();
  });

  it('retries loading accounts when the retry button is clicked', () => {
    accountServiceSpy.getAccounts.and.returnValue(throwError(() => new Error('network error')));
    fixture.detectChanges();

    accountServiceSpy.getAccounts.and.returnValue(of(mockAccounts));
    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    buttons.find((b) => b.textContent?.includes('Retry'))!.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('CHECKING');
  });
});
