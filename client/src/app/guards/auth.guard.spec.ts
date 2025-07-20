import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { canActivateAuth, canActivateProject } from './auth.guard';

describe('Auth Guards', () => {
  beforeEach(() => {
    const snackBarSpy = jasmine.createSpyObj('MatSnackBar', ['open']);

    TestBed.configureTestingModule({
      providers: [
        { provide: MatSnackBar, useValue: snackBarSpy }
      ]
    });
  });

  describe('Auth Guards', () => {
    it('should export canActivateAuth function', () => {
      expect(canActivateAuth).toBeDefined();
      expect(typeof canActivateAuth).toBe('function');
    });

    it('should export canActivateProject function', () => {
      expect(canActivateProject).toBeDefined();
      expect(typeof canActivateProject).toBe('function');
    });

    it('should have guards that can be called', () => {
      // These guards are created using Keycloak's createAuthGuard
      // In a real application they would need proper Keycloak setup
      // Here we just verify they exist and are functions
      expect(() => {
        // Guards exist and can be referenced
        const authGuard = canActivateAuth;
        const projectGuard = canActivateProject;
        expect(authGuard).toBeTruthy();
        expect(projectGuard).toBeTruthy();
      }).not.toThrow();
    });
  });
});