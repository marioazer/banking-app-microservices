export interface InternalTransferRequest {
  fromAccountId: number;
  toAccountId: number;
  amount: number;
}

export interface ExternalWireRequest {
  iban: string;
  swiftCode: string;
  beneficiaryName: string;
  amount: number;
}

export type TransferStatus = 'COMPLETED' | 'PENDING_APPROVAL' | 'REJECTED' | 'FAILED';

export interface TransferResponse {
  transactionId: string;
  status: TransferStatus;
}
