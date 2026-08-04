import { Component, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

import { AccountService } from '../../core/services/account.service';
import { AccountOverview, AccountType } from '../../core/models/account.models';
import { TableColumn, TableComponent } from '../../shared/table/table.component';
import { ButtonComponent } from '../../shared/button/button.component';
import { NavComponent } from '../../shared/nav/nav.component';
import { ModalComponent } from '../../shared/modal/modal.component';
import { InputComponent } from '../../shared/input/input.component';
import { AlertBannerComponent } from '../../shared/alert-banner/alert-banner.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    TableComponent,
    ButtonComponent,
    NavComponent,
    ModalComponent,
    FormsModule,
    InputComponent,
    AlertBannerComponent,
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css',
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

  readonly depositModalOpen = signal(false);
  readonly depositAccountId = signal<number | null>(null);
  readonly depositAmount = signal('');
  readonly depositError = signal<string | null>(null);
  readonly depositSuccess = signal<string | null>(null);

  readonly openAccountModalOpen = signal(false);
  readonly newAccountType = signal<AccountType>('SAVINGS');
  readonly openAccountError = signal<string | null>(null);

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

  openDepositModal(): void {
    this.depositError.set(null);
    this.depositSuccess.set(null);
    this.depositAmount.set('');
    this.depositAccountId.set(this.accounts()[0]?.accountId ?? null);
    this.depositModalOpen.set(true);
  }

  closeDepositModal(): void {
    this.depositModalOpen.set(false);
  }

  onDepositAccountChange(value: string): void {
    this.depositAccountId.set(value ? Number(value) : null);
  }

  submitDeposit(): void {
    this.depositError.set(null);
    this.depositSuccess.set(null);

    const amountNum = Number(this.depositAmount());
    if (!amountNum || amountNum <= 0) {
      this.depositError.set('Please enter a positive amount.');
      return;
    }
    if (this.depositAccountId() === null) {
      this.depositError.set('Please select an account.');
      return;
    }

    this.accountService.depositFunds(this.depositAccountId()!, amountNum).subscribe({
      next: (updated) => {
        this.accounts.update((accounts) =>
          accounts.map((a) => (a.accountId === updated.accountId ? updated : a)),
        );
        this.depositSuccess.set(`Deposited $${amountNum.toFixed(2)} successfully.`);
        this.depositAmount.set('');
      },
      error: (error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 400) {
          this.depositError.set(error.error?.message ?? 'Deposit amount is invalid.');
        } else {
          this.depositError.set('Something went wrong. Please try again.');
        }
      },
    });
  }

  openNewAccountModal(): void {
    this.openAccountError.set(null);
    this.newAccountType.set('SAVINGS');
    this.openAccountModalOpen.set(true);
  }

  closeNewAccountModal(): void {
    this.openAccountModalOpen.set(false);
  }

  onNewAccountTypeChange(value: string): void {
    this.newAccountType.set(value as AccountType);
  }

  submitOpenAccount(): void {
    this.openAccountError.set(null);

    this.accountService.openAccount(this.newAccountType()).subscribe({
      next: (created) => {
        this.accounts.update((accounts) => [...accounts, created]);
        this.openAccountModalOpen.set(false);
      },
      error: (error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 400) {
          this.openAccountError.set(error.error?.message ?? 'Could not open account.');
        } else {
          this.openAccountError.set('Something went wrong. Please try again.');
        }
      },
    });
  }
}
