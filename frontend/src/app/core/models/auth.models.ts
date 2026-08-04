export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginSuccessResponse {
  status: 'SUCCESS';
  access_token: string;
}

export interface TwoFaRequiredResponse {
  status: '2FA_REQUIRED';
  pre_auth_token: string;
}

export type LoginResponse = LoginSuccessResponse | TwoFaRequiredResponse;

export interface VerifyTwoFaRequest {
  code: string;
}

export interface RefreshResponse {
  access_token: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
  phoneNumber: string;
}

export interface RegisterResponse {
  status: 'SUCCESS';
  message: string;
}
