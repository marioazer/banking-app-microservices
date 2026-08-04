import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';

import { AccountService } from './account.service';
import { environment } from '../../../environments/environment';
import { AccountOverview, TransactionPage } from '../models/account.models';

describe('AccountService', () => {
  let service: AccountService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AccountService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AccountService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('fetches the list of accounts for the logged-in user', () => {
    const mockAccounts: AccountOverview[] = [
      {
        accountId: 1,
        accountType: 'CHECKING',
        availableBalance: 1204.55,
        routingNumber: '021000021',
        maskedAccountNumber: '****1234',
        status: 'ACTIVE',
      },
    ];
    let result: AccountOverview[] | undefined;

    service.getAccounts().subscribe((accounts) => (result = accounts));

    const req = httpMock.expectOne(environment.accountApiUrl);
    expect(req.request.method).toBe('GET');
    req.flush(mockAccounts);

    expect(result).toEqual(mockAccounts);
  });

  it('fetches a page of transactions for an account with default paging', () => {
    const mockPage: TransactionPage = {
      content: [
        { id: 1, accountId: 1, transactionType: 'DEBIT', amount: 4.5, description: 'Coffee Shop', createdAt: '2026-08-01T10:00:00Z' },
      ],
      totalPages: 1,
      totalElements: 1,
      number: 0,
      size: 50,
    };
    let result: TransactionPage | undefined;

    service.getTransactions(1, {}).subscribe((page) => (result = page));

    const req = httpMock.expectOne(
      (r) => r.url === `${environment.accountApiUrl}/1/transactions` && r.method === 'GET',
    );
    expect(req.request.params.has('type')).toBeFalse();
    req.flush(mockPage);

    expect(result).toEqual(mockPage);
  });

  it('includes the type filter and page number as query params when provided', () => {
    const mockPage: TransactionPage = { content: [], totalPages: 0, totalElements: 0, number: 2, size: 50 };

    service.getTransactions(1, { type: 'CREDIT', page: 2 }).subscribe();

    const req = httpMock.expectOne(
      (r) => r.url === `${environment.accountApiUrl}/1/transactions`,
    );
    expect(req.request.params.get('type')).toBe('CREDIT');
    expect(req.request.params.get('page')).toBe('2');
    req.flush(mockPage);
  });
});
