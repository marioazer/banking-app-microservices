# Frontend

Angular 22 web client for the banking app backend (`../01-auth-service`, `../02-profile-service`, `../03-account-service`, `../03-transaction-service`). Standalone components, signal-based state, plain CSS.

See the [root README](../README.md#running-this-project) for how to start the backend — this app has nothing to talk to without it.

## Development server

```bash
npm install
npm run start        # ng serve, http://localhost:4200
```

The four backend services must already be running (see root README) — each has a `CorsConfigurationSource` bean scoped to `http://localhost:4200` so the browser can call them directly.

## Pages

`/signup` (self-service registration) → `/login` (credentials + SMS 2FA) → `/dashboard` (accounts) → `/accounts/:id/transactions` → `/transfer` (internal + external, KYC-gated) → `/profile` (contact info + KYC status) → `/profile/alerts` (threshold + daily summary).

Registering triggers a Kafka event that automatically provisions a `PENDING_VERIFICATION` profile and a `$0` checking account for the new user (requires `auth-service`, `profile-service`, and `account-service` all running, plus Kafka) — see the root README's [Frontend](../README.md#frontend) section.

## Testing

Unit tests (Karma/Jasmine):

```bash
npm test
```

End-to-end (Cypress) — needs the full backend, `ng serve`, and a seeded test user already running:

```bash
npx cypress run --spec "cypress/e2e/user-journey.cy.ts"
```

The E2E test skips the 2FA step by pre-setting a `Device-ID` cookie matching a `recognized_devices` row seeded for the test user (see root README).

## Building

```bash
npm run build         # output in dist/
```

## Code scaffolding

```bash
ng generate component component-name
```

For a complete list of available schematics, run `ng generate --help`. For more on the Angular CLI, see the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli).
