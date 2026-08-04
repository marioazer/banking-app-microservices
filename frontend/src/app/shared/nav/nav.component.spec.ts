import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { NavComponent } from './nav.component';
import { AuthService } from '../../core/auth.service';

describe('NavComponent', () => {
  let fixture: ComponentFixture<NavComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let router: Router;

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['logout']);

    TestBed.configureTestingModule({
      imports: [NavComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: authServiceSpy }],
    });

    fixture = TestBed.createComponent(NavComponent);
    router = TestBed.inject(Router);
    spyOn(router, 'navigate');
    fixture.detectChanges();
  });

  it('renders links to dashboard, transfer, profile, and alerts', () => {
    const links: HTMLAnchorElement[] = Array.from(fixture.nativeElement.querySelectorAll('a'));
    const hrefs = links.map((a) => a.getAttribute('href'));

    expect(hrefs).toContain('/dashboard');
    expect(hrefs).toContain('/transfer');
    expect(hrefs).toContain('/profile');
    expect(hrefs).toContain('/profile/alerts');
  });

  it('logs out and navigates to /login when Logout is clicked', () => {
    authServiceSpy.logout.and.returnValue(of({}));

    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    buttons.find((b) => b.textContent?.includes('Logout'))!.click();

    expect(authServiceSpy.logout).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });
});
