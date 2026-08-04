import { Component, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';

import { AccountService } from '../../core/services/account.service';
import { AccountOverview } from '../../core/models/account.models';
import { TableColumn, TableComponent } from '../../shared/table/table.component';
import { ButtonComponent } from '../../shared/button/button.component';
import { NavComponent } from '../../shared/nav/nav.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [TableComponent, ButtonComponent, NavComponent],
  templateUrl: './dashboard.component.html',
})
export class DashboardComponent implements OnInit {
  readonly columns: TableColumn[] = [
    { key: 'accountType', label: 'Type' },
    { key: 'maskedAccountNumber', label: 'Account' },
    { key: 'status', label: 'Status' },
    { key: 'availableBalance', label: 'Balance' },
  ];

  readonly accounts = signal<AccountOverview[]>([]);
  readonly loading = signal(false);
  readonly error = signal(false);

  constructor(
    private readonly accountService: AccountService,
    private readonly router: Router,
  ) {}

  ngOnInit(): void {
    this.loadAccounts();
  }

  loadAccounts(): void {
    this.loading.set(true);
    this.error.set(false);

    this.accountService.getAccounts().subscribe({
      next: (accounts) => {
        this.accounts.set(accounts);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  onRowClick(row: AccountOverview): void {
    this.router.navigate(['/accounts', row.accountId, 'transactions']);
  }
}
