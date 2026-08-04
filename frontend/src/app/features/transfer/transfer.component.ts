import { Component, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';

import { AccountService } from '../../core/services/account.service';
import { TransferService } from '../../core/services/transfer.service';
import { AccountOverview } from '../../core/models/account.models';
import { TransferResponse } from '../../core/models/transfer.models';
import { ButtonComponent } from '../../shared/button/button.component';
import { AlertBannerComponent } from '../../shared/alert-banner/alert-banner.component';
import { NavComponent } from '../../shared/nav/nav.component';
import { InputComponent } from '../../shared/input/input.component';

type Tab = 'internal' | 'external';
type ResultType = 'success' | 'error' | 'info';

const IBAN_PATTERN = /^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$/;

@Component({
  selector: 'app-transfer',
  standalone: true,
  imports: [FormsModule, ButtonComponent, AlertBannerComponent, NavComponent, InputComponent],
  templateUrl: './transfer.component.html',
  styleUrl: './transfer.component.css',
})
export class TransferComponent implements OnInit {
  readonly accounts = signal<AccountOverview[]>([]);
  readonly activeTab = signal<Tab>('internal');

  readonly fromAccountId = signal<number | null>(null);
  readonly toAccountId = signal<number | null>(null);
  readonly amount = signal('');

  readonly extFromAccountId = signal<number | null>(null);
  readonly iban = signal('');
  readonly swiftCode = signal('');
  readonly beneficiaryName = signal('');
  readonly extAmount = signal('');

  readonly validationError = signal<string | null>(null);
  readonly resultMessage = signal<string | null>(null);
  readonly resultType = signal<ResultType>('info');

  constructor(
    private readonly accountService: AccountService,
    private readonly transferService: TransferService,
  ) {}

  ngOnInit(): void {
    this.accountService.getAccounts().subscribe((accounts) => this.accounts.set(accounts));
  }

  selectTab(tab: Tab): void {
    this.activeTab.set(tab);
    this.validationError.set(null);
    this.resultMessage.set(null);
  }

  onFromAccountChange(value: string): void {
    this.fromAccountId.set(value ? Number(value) : null);
  }

  onToAccountChange(value: string): void {
    this.toAccountId.set(value ? Number(value) : null);
  }

  onExtFromAccountChange(value: string): void {
    this.extFromAccountId.set(value ? Number(value) : null);
  }

  submitInternal(): void {
    this.validationError.set(null);
    this.resultMessage.set(null);

    const amountNum = Number(this.amount());
    if (!amountNum || amountNum <= 0) {
      this.validationError.set('Please enter a positive amount.');
      return;
    }
    if (this.fromAccountId() === null || this.toAccountId() === null) {
      this.validationError.set('Please select both accounts.');
      return;
    }

    this.transferService
      .transferInternal({
        fromAccountId: this.fromAccountId()!,
        toAccountId: this.toAccountId()!,
        amount: amountNum,
      })
      .subscribe({
        next: (response) => this.handleResult(response),
        error: (error: unknown) => this.handleError(error),
      });
  }

  submitExternal(): void {
    this.validationError.set(null);
    this.resultMessage.set(null);

    const amountNum = Number(this.extAmount());
    if (!amountNum || amountNum <= 0) {
      this.validationError.set('Please enter a positive amount.');
      return;
    }
    if (this.extFromAccountId() === null) {
      this.validationError.set('Please select an account.');
      return;
    }
    if (!IBAN_PATTERN.test(this.iban())) {
      this.validationError.set('Please enter a valid IBAN.');
      return;
    }
    if (!this.swiftCode() || !this.beneficiaryName()) {
      this.validationError.set('Please fill in all fields.');
      return;
    }

    this.transferService
      .transferExternal(this.extFromAccountId()!, {
        iban: this.iban(),
        swiftCode: this.swiftCode(),
        beneficiaryName: this.beneficiaryName(),
        amount: amountNum,
      })
      .subscribe({
        next: (response) => this.handleResult(response),
        error: (error: unknown) => this.handleError(error),
      });
  }

  private handleResult(response: TransferResponse): void {
    if (response.status === 'PENDING_APPROVAL') {
      this.resultType.set('info');
      this.resultMessage.set(`Your transfer is under review. Transaction ID: ${response.transactionId}`);
    } else if (response.status === 'COMPLETED') {
      this.resultType.set('success');
      this.resultMessage.set(`Transfer successful! Transaction ID: ${response.transactionId}`);
    } else {
      this.resultType.set('error');
      this.resultMessage.set(`Transfer ${response.status.toLowerCase()}. Transaction ID: ${response.transactionId}`);
    }
  }

  private handleError(error: unknown): void {
    this.resultType.set('error');
    if (error instanceof HttpErrorResponse && error.status === 403) {
      this.resultMessage.set('Please verify your identity to enable transfers.');
    } else {
      this.resultMessage.set('Something went wrong. Please try again.');
    }
  }
}
