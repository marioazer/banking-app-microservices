import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ExternalWireRequest, InternalTransferRequest, TransferResponse } from '../models/transfer.models';

@Injectable({ providedIn: 'root' })
export class TransferService {
  private readonly baseUrl = environment.transactionApiUrl;

  constructor(private readonly http: HttpClient) {}

  transferInternal(request: InternalTransferRequest): Observable<TransferResponse> {
    return this.http.post<TransferResponse>(`${this.baseUrl}/internal`, request);
  }

  transferExternal(fromAccountId: number, request: ExternalWireRequest): Observable<TransferResponse> {
    const params = new HttpParams().set('fromAccountId', fromAccountId);
    return this.http.post<TransferResponse>(`${this.baseUrl}/external`, request, { params });
  }
}
