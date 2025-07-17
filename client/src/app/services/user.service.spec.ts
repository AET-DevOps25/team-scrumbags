import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { UserService } from './user.service';
import { UserApi } from './user.api';
import { UserState } from '../states/user.state';
import { User } from '../models/user.model';
import Keycloak from 'keycloak-js';

describe('UserService', () => {
  let service: UserService;
  let userApiSpy: jasmine.SpyObj<UserApi>;
  let userStateSpy: jasmine.SpyObj<UserState>;
  let keycloakSpy: jasmine.SpyObj<Keycloak>;

  const mockUsers: User[] = [
    { id: '1', username: 'user1', email: 'user1@example.com' },
    { id: '2', username: 'user2', email: 'user2@example.com' }
  ];

  beforeEach(() => {
    const userApiSpyObj = jasmine.createSpyObj('UserApi', ['getAllUsers']);
    const userStateSpyObj = jasmine.createSpyObj('UserState', ['setAllUsers']);
    const keycloakSpyObj = jasmine.createSpyObj('Keycloak', [], {
      tokenParsed: {
        sub: 'test-user-id',
        preferred_username: 'testuser',
        email: 'test@example.com'
      }
    });

    TestBed.configureTestingModule({
      providers: [
        UserService,
        { provide: UserApi, useValue: userApiSpyObj },
        { provide: UserState, useValue: userStateSpyObj },
        { provide: Keycloak, useValue: keycloakSpyObj }
      ]
    });

    service = TestBed.inject(UserService);
    userApiSpy = TestBed.inject(UserApi) as jasmine.SpyObj<UserApi>;
    userStateSpy = TestBed.inject(UserState) as jasmine.SpyObj<UserState>;
    keycloakSpy = TestBed.inject(Keycloak) as jasmine.SpyObj<Keycloak>;
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

    it('should return undefined when no token available', () => {
      // Reset the tokenParsed to undefined
      Object.defineProperty(keycloakSpy, 'tokenParsed', {
        value: undefined,
        writable: true
      });

      const result = service.getSignedInUser();

      expect(result).toBeUndefined();
    });

    it('should handle missing token fields gracefully', () => {
      // Set partial token data
      Object.defineProperty(keycloakSpy, 'tokenParsed', {
        value: { sub: 'test-id' }, // missing username and email
        writable: true
      });

      const result = service.getSignedInUser();

      expect(result).toEqual({
        id: 'test-id',
        username: '',
        email: ''
      });
    });
  });

  describe('loadAllUsers', () => {
    it('should load users and update state', () => {
      userApiSpy.getAllUsers.and.returnValue(of(mockUsers));

      const result$ = service.loadAllUsers();

      result$.subscribe(users => {
        expect(users).toEqual(mockUsers);
        expect(userStateSpy.setAllUsers).toHaveBeenCalledWith(mockUsers);
      });

      expect(userApiSpy.getAllUsers).toHaveBeenCalled();
    });

    it('should set loading state during API call', () => {
      userApiSpy.getAllUsers.and.returnValue(of(mockUsers));

      // Initially not loading
      expect(service.isLoadingAllUsers()).toBeFalse();

      const result$ = service.loadAllUsers();

      // Should be loading during the call
      expect(service.isLoadingAllUsers()).toBeTrue();

      result$.subscribe(() => {
        // Should not be loading after completion
        expect(service.isLoadingAllUsers()).toBeFalse();
      });
    });

    it('should handle API errors properly', () => {
      const errorMessage = 'API Error';
      userApiSpy.getAllUsers.and.throwError(errorMessage);

      const result$ = service.loadAllUsers();

      result$.subscribe({
        error: (error) => {
          expect(error).toBe(errorMessage);
          expect(service.isLoadingAllUsers()).toBeFalse();
        }
      });
    });
  });
});