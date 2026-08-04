export type AccountType = 'CHECKING' | 'SAVINGS';
export type AccountStatus = 'ACTIVE' | 'FROZEN' | 'CLOSED';
export type TransactionType = 'CREDIT' | 'DEBIT';

export interface AccountOverview {
  accountId: number;
  accountType: AccountType;
  availableBalance: number;
  routingNumber: string;
  maskedAccountNumber: string;
  status: AccountStatus;
}

export interface AccountTransaction {
  id: number;
  accountId: number;
  transactionType: TransactionType;
  amount: number;
  description: string;
  createdAt: string;
}

export interface TransactionPage {
  content: AccountTransaction[];
  totalPages: number;
  totalElements: number;
  number: number;
  size: number;
}
