import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { AccountService } from '../../core/services/account.service';
import { AccountTransaction, TransactionType } from '../../core/models/account.models';
import { TableColumn, TableComponent } from '../../shared/table/table.component';
import { ButtonComponent } from '../../shared/button/button.component';
import { NavComponent } from '../../shared/nav/nav.component';

type TypeFilter = 'ALL' | TransactionType;

@Component({
  selector: 'app-account-transactions',
  standalone: true,
  imports: [TableComponent, ButtonComponent, NavComponent],
  templateUrl: './account-transactions.component.html',
})
export class AccountTransactionsComponent implements OnInit {
  readonly columns: TableColumn[] = [
    { key: 'createdAt', label: 'Date' },
    { key: 'transactionType', label: 'Type' },
    { key: 'description', label: 'Description' },
    { key: 'amount', label: 'Amount' },
  ];

  readonly transactions = signal<AccountTransaction[]>([]);
  readonly currentPage = signal(0);
  readonly totalPages = signal(0);
  readonly typeFilter = signal<TypeFilter>('ALL');

  private accountId!: number;

  constructor(
    private readonly accountService: AccountService,
    private readonly route: ActivatedRoute,
  ) {}

  ngOnInit(): void {
    this.accountId = Number(this.route.snapshot.paramMap.get('id'));
    this.loadPage(0);
  }

  onFilterChange(value: string): void {
    this.typeFilter.set(value as TypeFilter);
    this.loadPage(0);
  }

  goToPage(page: number): void {
    this.loadPage(page);
  }

  private loadPage(page: number): void {
    const filter = this.typeFilter();
    this.accountService
      .getTransactions(this.accountId, {
        ...(filter !== 'ALL' ? { type: filter } : {}),
        page,
      })
      .subscribe((result) => {
        this.transactions.set(result.content);
        this.currentPage.set(result.number);
        this.totalPages.set(result.totalPages);
      });
  }
}
