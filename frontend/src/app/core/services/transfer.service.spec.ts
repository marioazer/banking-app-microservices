import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';

import { TransferService } from './transfer.service';
import { environment } from '../../../environments/environment';
import { TransferResponse } from '../models/transfer.models';

describe('TransferService', () => {
  let service: TransferService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [TransferService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TransferService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('posts an internal transfer request', () => {
    const mockResponse: TransferResponse = { transactionId: 'txn-1', status: 'COMPLETED' };
    let result: TransferResponse | undefined;

    service
      .transferInternal({ fromAccountId: 1, toAccountId: 2, amount: 50 })
      .subscribe((res) => (result = res));

    const req = httpMock.expectOne(`${environment.transactionApiUrl}/internal`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ fromAccountId: 1, toAccountId: 2, amount: 50 });
    req.flush(mockResponse);

    expect(result).toEqual(mockResponse);
  });

  it('posts an external wire request with fromAccountId as a query param', () => {
    const mockResponse: TransferResponse = { transactionId: 'txn-2', status: 'PENDING_APPROVAL' };
    let result: TransferResponse | undefined;

    service
      .transferExternal(1, {
        iban: 'GB29NWBK60161331926819',
        swiftCode: 'NWBKGB2L',
        beneficiaryName: 'Jane Doe',
        amount: 5000,
      })
      .subscribe((res) => (result = res));

    const req = httpMock.expectOne(
      (r) => r.url === `${environment.transactionApiUrl}/external` && r.params.get('fromAccountId') === '1',
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      iban: 'GB29NWBK60161331926819',
      swiftCode: 'NWBKGB2L',
      beneficiaryName: 'Jane Doe',
      amount: 5000,
    });
    req.flush(mockResponse);

    expect(result).toEqual(mockResponse);
  });
});
