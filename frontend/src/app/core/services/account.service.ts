import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AccountOverview, AccountType, TransactionPage, TransactionType } from '../models/account.models';

export interface TransactionQuery {
  type?: TransactionType;
  page?: number;
}

@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly baseUrl = environment.accountApiUrl;

  constructor(private readonly http: HttpClient) {}

  getAccounts(): Observable<AccountOverview[]> {
    return this.http.get<AccountOverview[]>(this.baseUrl);
  }

  getTransactions(accountId: number, query: TransactionQuery): Observable<TransactionPage> {
    let params = new HttpParams();
    if (query.type) {
      params = params.set('type', query.type);
    }
    if (query.page !== undefined) {
      params = params.set('page', query.page);
    }

    return this.http.get<TransactionPage>(`${this.baseUrl}/${accountId}/transactions`, { params });
  }

  depositFunds(accountId: number, amount: number): Observable<AccountOverview> {
    return this.http.post<AccountOverview>(`${this.baseUrl}/${accountId}/deposit`, { amount });
  }

  openAccount(accountType: AccountType): Observable<AccountOverview> {
    return this.http.post<AccountOverview>(this.baseUrl, { accountType });
  }

  seedDemoTransactions(accountId: number): Observable<AccountOverview> {
    return this.http.post<AccountOverview>(`${this.baseUrl}/${accountId}/demo-transactions`, {});
  }
}
