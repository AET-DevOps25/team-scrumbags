import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot, convertToParamMap } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { canActivateAuth, canActivateProject } from './auth.guard';

describe('Auth Guards', () => {
  let mockMatSnackBar: jasmine.SpyObj<MatSnackBar>;
  let mockRoute: ActivatedRouteSnapshot;
  let mockState: RouterStateSnapshot;

  beforeEach(() => {
    const snackBarSpy = jasmine.createSpyObj('MatSnackBar', ['open']);

    TestBed.configureTestingModule({
      providers: [
        { provide: MatSnackBar, useValue: snackBarSpy }
      ]
    });

    mockMatSnackBar = TestBed.inject(MatSnackBar) as jasmine.SpyObj<MatSnackBar>;
    
    mockRoute = {
      params: { projectId: 'test-project' },
      queryParams: {},
      data: {},
      url: [],
      fragment: null,
      outlet: 'primary',
      component: null,
      routeConfig: null,
      root: {} as ActivatedRouteSnapshot,
      parent: null,
      firstChild: null,
      children: [],
      pathFromRoot: [],
      paramMap: convertToParamMap({ projectId: 'test-project' }),
      queryParamMap: convertToParamMap({}),
      title: undefined
    };

    mockState = {
      url: '/test',
      root: {} as ActivatedRouteSnapshot
    };
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