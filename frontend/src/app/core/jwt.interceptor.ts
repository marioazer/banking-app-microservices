import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';

import { AuthService } from './auth.service';
import { environment } from '../../environments/environment';

export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.accessToken();
  const isAuthServiceRequest = req.url.startsWith(environment.authApiUrl);

  const authorizedReq = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authorizedReq).pipe(
    catchError((error: unknown) => {
      if (!isAuthServiceRequest && error instanceof HttpErrorResponse && error.status === 401 && token) {
        return authService.refresh().pipe(
          switchMap((refreshed) =>
            next(
              req.clone({ setHeaders: { Authorization: `Bearer ${refreshed.access_token}` } }),
            ),
          ),
          catchError((refreshError: unknown) => {
            authService.clearSession();
            return throwError(() => refreshError);
          }),
        );
      }
      return throwError(() => error);
    }),
  );
};
