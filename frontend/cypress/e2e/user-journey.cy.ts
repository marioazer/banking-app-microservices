describe('Banking app user journey', () => {
  beforeEach(() => {
    cy.setCookie('Device-ID', 'e2e-test-device-000', { domain: 'localhost', secure: true });
  });

  it('logs in, views accounts, makes an internal transfer, and logs out', () => {
    cy.visit('/login');

    cy.get('app-input[id="username"] input').type('e2etest');
    cy.get('app-input[id="password"] input').type('Password123!');
    cy.contains('button', 'Log In').click();

    cy.url().should('include', '/dashboard');
    cy.contains('CHECKING').should('be.visible');
    cy.contains('SAVINGS').should('be.visible');

    cy.contains('a', 'Transfer').click();
    cy.url().should('include', '/transfer');

    cy.get('select').eq(0).select('3');
    cy.get('select').eq(1).select('4');
    cy.get('input[name="amount"]').type('25');
    cy.contains('button', 'Send').click();

    cy.contains(/successful/i).should('be.visible');

    cy.contains('button', 'Logout').click();
    cy.url().should('include', '/login');
  });
});
