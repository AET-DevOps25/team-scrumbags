import { TestBed } from '@angular/core/testing';
import { of, throwError, from } from 'rxjs';
import { fakeAsync, tick } from '@angular/core/testing';
import { UserService } from './user.service';
import { UserApi } from './user.api';
import { UserState } from '../states/user.state';
import { User } from '../models/user.model';
import Keycloak from 'keycloak-js';

describe('UserService', () => {
  let service: UserService;
  let mockUserApi: jasmine.SpyObj<UserApi>;
  let mockUserState: jasmine.SpyObj<UserState>;
  let mockKeycloak: jasmine.SpyObj<Keycloak>;

  const mockUsers: User[] = [
    { id: 'user1', username: 'john_doe', email: 'john@example.com' },
    { id: 'user2', username: 'jane_smith', email: 'jane@example.com' }
  ];

  beforeEach(() => {
    const userApiSpy = jasmine.createSpyObj('UserApi', ['getAllUsers']);
    const userStateSpy = jasmine.createSpyObj('UserState', ['setAllUsers']);
    
    // Create a mock Keycloak object with mutable tokenParsed
    const keycloakSpy = {
      tokenParsed: {
        sub: 'test-user-id',
        preferred_username: 'testuser',
        email: 'test@example.com'
      }
    };

    TestBed.configureTestingModule({
      providers: [
        UserService,
        { provide: UserApi, useValue: userApiSpy },
        { provide: UserState, useValue: userStateSpy },
        { provide: Keycloak, useValue: keycloakSpy }
      ]
    });

    service = TestBed.inject(UserService);
    mockUserApi = TestBed.inject(UserApi) as jasmine.SpyObj<UserApi>;
    mockUserState = TestBed.inject(UserState) as jasmine.SpyObj<UserState>;
    mockKeycloak = TestBed.inject(Keycloak) as jasmine.SpyObj<Keycloak>;
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getSignedInUser', () => {
    it('should return user from keycloak token', () => {
      const result = service.getSignedInUser();
      
      expect(result).toEqual({
        id: 'test-user-id',
        username: 'testuser',
        email: 'test@example.com'
      });
    });

    it('should handle missing token fields gracefully', () => {
      (mockKeycloak as unknown as { tokenParsed: Partial<typeof mockKeycloak.tokenParsed> }).tokenParsed = {
        sub: 'test-user-id'
        // missing preferred_username and email
      };
      
      const result = service.getSignedInUser();
      
      expect(result).toEqual({
        id: 'test-user-id',
        username: '',
        email: ''
      });
    });

    it('should return undefined when no token available', () => {
      (mockKeycloak as unknown as { tokenParsed: null }).tokenParsed = null;
      
      const result = service.getSignedInUser();
      
      expect(result).toBeUndefined();
    });
  });

  describe('loadAllUsers', () => {
    it('should load users and update state', () => {
      mockUserApi.getAllUsers.and.returnValue(of(mockUsers));
      
      service.loadAllUsers().subscribe(users => {
        expect(users).toEqual(mockUsers);
      });
      
      expect(mockUserApi.getAllUsers).toHaveBeenCalled();
      expect(mockUserState.setAllUsers).toHaveBeenCalledWith(mockUsers);
    });

    it('should set loading state during API call', fakeAsync(() => {
      // Create a delayed observable to test loading state
      let resolveUsers: (users: User[]) => void;
      const delayedUsers$ = new Promise<User[]>((resolve) => {
        resolveUsers = resolve;
      });
      
      mockUserApi.getAllUsers.and.returnValue(from(delayedUsers$));
      
      expect(service.isLoadingAllUsers()).toBe(false);
      
      service.loadAllUsers().subscribe();
      tick(); // Allow synchronous operations to complete
      
      expect(service.isLoadingAllUsers()).toBe(true);
      
      // Resolve the promise and complete the observable
      resolveUsers!(mockUsers);
      tick();
      
      expect(service.isLoadingAllUsers()).toBe(false);
    }));

    it('should handle API errors properly', fakeAsync(() => {
      const error = new Error('API Error');
      mockUserApi.getAllUsers.and.returnValue(throwError(() => error));
      
      expect(service.isLoadingAllUsers()).toBe(false);
      
      service.loadAllUsers().subscribe({
        error: (err) => {
          expect(err).toBe(error);
        }
      });
      
      tick();
      expect(service.isLoadingAllUsers()).toBe(false);
    }));
  });
});
