import { TestBed } from '@angular/core/testing';
import { Router, NavigationEnd } from '@angular/router';
import { of, Subject, from, Observable } from 'rxjs';
import { fakeAsync, tick } from '@angular/core/testing';
import { ProjectService } from './project.service';
import { ProjectApi } from './project.api';
import { ProjectState } from '../states/project.state';
import { Project } from '../models/project.model';
import { User } from '../models/user.model';
import Keycloak from 'keycloak-js';

describe('ProjectService', () => {
  let service: ProjectService;
  let mockProjectApi: jasmine.SpyObj<ProjectApi>;
  let mockProjectState: jasmine.SpyObj<ProjectState>;
  let mockKeycloak: jasmine.SpyObj<Keycloak>;
  let routerEvents: Subject<NavigationEnd>;

  const mockProject: Project = {
    id: 'project-1',
    name: 'Test Project',
    description: 'Test Description',
    users: [],
    meetingNotes: new Map(),
    reports: new Map(),
    messages: new Map()
  };

  const mockUsers: User[] = [
    { id: 'user1', username: 'john_doe', email: 'john@example.com' },
    { id: 'user2', username: 'jane_smith', email: 'jane@example.com' }
  ];

  beforeEach(() => {
    routerEvents = new Subject();
    const projectApiSpy = jasmine.createSpyObj('ProjectApi', [
      'getProjectList', 'getProjectById', 'createProject', 
      'getUsersInProject', 'assignUserToProject', 'removeUserFromProject'
    ]);
    const projectStateSpy = jasmine.createSpyObj('ProjectState', [
      'setProjectList', 'updateProject', 'setProjectById', 
      'setUsersOfProject', 'addUserToProject', 'removeUserFromProject',
      'allProjects'
    ]);
    const routerSpy = jasmine.createSpyObj('Router', [], {
      events: routerEvents.asObservable(),
      url: '/projects/project-1'
    });
    const keycloakSpy = jasmine.createSpyObj('Keycloak', ['updateToken']);

    TestBed.configureTestingModule({
      providers: [
        ProjectService,
        { provide: ProjectApi, useValue: projectApiSpy },
        { provide: ProjectState, useValue: projectStateSpy },
        { provide: Router, useValue: routerSpy },
        { provide: Keycloak, useValue: keycloakSpy }
      ]
    });

    service = TestBed.inject(ProjectService);
    mockProjectApi = TestBed.inject(ProjectApi) as jasmine.SpyObj<ProjectApi>;
    mockProjectState = TestBed.inject(ProjectState) as jasmine.SpyObj<ProjectState>;
    mockKeycloak = TestBed.inject(Keycloak) as jasmine.SpyObj<Keycloak>;
    
    // Set up default return values to prevent undefined errors
    mockProjectApi.getProjectById.and.returnValue(of(mockProject));
    mockProjectState.allProjects.and.returnValue(new Map([['project-1', mockProject]]));
  });

  afterEach(() => {
    // Clean up subscriptions and subjects
    routerEvents.complete();
    if (service) {
      service.ngOnDestroy();
    }
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('constructor', () => {
    it('should subscribe to router events and extract project ID', (done) => {
      // The service constructor sets up the router subscription
      // We need to test the behavior when navigation events occur
      
      routerEvents.next(new NavigationEnd(1, '/projects/project-1', '/projects/project-1'));
      
      // Wait for async operations to complete
      setTimeout(() => {
        expect(mockProjectApi.getProjectById).toHaveBeenCalledWith('project-1');
        done();
      }, 0);
    });

    it('should handle URLs without project ID', () => {
      // Test the internal selectProject method directly with undefined
      const selectProjectSpy = spyOn(service as unknown as { selectProject: (projectId: string | undefined) => Observable<Project | undefined> }, 'selectProject').and.callThrough();
      
      // Call selectProject with undefined (simulating no project ID in URL)
      (service as unknown as { selectProject: (projectId: string | undefined) => Observable<Project | undefined> }).selectProject(undefined).subscribe();
      
      expect(selectProjectSpy).toHaveBeenCalledWith(undefined);
      expect(service.selectedProjectId()).toBeUndefined();
    });
  });

  describe('loadProjectList', () => {
    it('should load project list and update state', () => {
      const projects = [mockProject];
      mockProjectApi.getProjectList.and.returnValue(of(projects));
      
      service.loadProjectList().subscribe(result => {
        expect(result).toEqual(projects);
      });
      
      expect(mockProjectApi.getProjectList).toHaveBeenCalled();
      expect(mockProjectState.setProjectList).toHaveBeenCalledWith(projects);
    });

    it('should set loading state during API call', fakeAsync(() => {
      const projects = [mockProject];
      
      // Create a delayed observable to test loading state
      let resolveProjects: (projects: Project[]) => void;
      const delayedProjects$ = new Promise<Project[]>((resolve) => {
        resolveProjects = resolve;
      });
      
      mockProjectApi.getProjectList.and.returnValue(from(delayedProjects$));
      
      expect(service.isLoadingProjectList()).toBe(false);
      
      service.loadProjectList().subscribe();
      tick(); // Allow synchronous operations to complete
      
      expect(service.isLoadingProjectList()).toBe(true);
      
      // Resolve the promise and complete the observable
      resolveProjects!(projects);
      tick();
      
      expect(service.isLoadingProjectList()).toBe(false);
    }));
  });

  describe('createProject', () => {
    it('should create project and update state', () => {
      mockProjectApi.createProject.and.returnValue(of(mockProject));
      
      service.createProject(mockProject).subscribe(result => {
        expect(result).toEqual(mockProject);
      });
      
      expect(mockProjectApi.createProject).toHaveBeenCalledWith(mockProject);
      expect(mockKeycloak.updateToken).toHaveBeenCalledWith(Number.MAX_VALUE);
      expect(mockProjectState.setProjectById).toHaveBeenCalledWith(mockProject.id, mockProject);
    });
  });

  describe('loadUsersOfProject', () => {
    it('should load users and update state', () => {
      mockProjectApi.getUsersInProject.and.returnValue(of(mockUsers));
      
      service.loadUsersOfProject('project-1').subscribe(result => {
        expect(result).toEqual(mockUsers);
      });
      
      expect(mockProjectApi.getUsersInProject).toHaveBeenCalledWith('project-1');
      expect(mockProjectState.setUsersOfProject).toHaveBeenCalledWith('project-1', mockUsers);
    });
  });

  describe('assignUserToProject', () => {
    it('should assign users and update state', () => {
      const userIds = ['user1', 'user2'];
      mockProjectApi.assignUserToProject.and.returnValue(of(userIds));
      
      service.assignUserToProject('project-1', mockUsers).subscribe();
      
      expect(mockProjectApi.assignUserToProject).toHaveBeenCalledWith('project-1', userIds);
      expect(mockProjectState.addUserToProject).toHaveBeenCalledTimes(2);
    });

    it('should handle partial assignment results', () => {
      const assignedUserIds = ['user1']; // Only one user assigned
      mockProjectApi.assignUserToProject.and.returnValue(of(assignedUserIds));
      
      service.assignUserToProject('project-1', mockUsers).subscribe();
      
      expect(mockProjectState.addUserToProject).toHaveBeenCalledTimes(1);
      expect(mockProjectState.addUserToProject).toHaveBeenCalledWith('project-1', mockUsers[0]);
    });
  });

  describe('removeUserFromProject', () => {
    it('should remove users and update state', () => {
      const userIds = ['user1', 'user2'];
      mockProjectApi.removeUserFromProject.and.returnValue(of(userIds));
      
      service.removeUserFromProject('project-1', userIds).subscribe();
      
      expect(mockProjectApi.removeUserFromProject).toHaveBeenCalledWith('project-1', userIds);
      expect(mockProjectState.removeUserFromProject).toHaveBeenCalledTimes(2);
    });
  });

  describe('selectedProject computed', () => {
    beforeEach(() => {
      mockProjectState.allProjects.and.returnValue(new Map([['project-1', mockProject]]));
    });

    it('should return selected project from state', () => {
      mockProjectApi.getProjectById.and.returnValue(of(mockProject));
      
      // Simulate navigation to project
      routerEvents.next(new NavigationEnd(1, '/projects/project-1', '/projects/project-1'));
      
      expect(service.selectedProject()).toEqual(mockProject);
    });

    it('should return undefined when no project is selected', () => {
      expect(service.selectedProject()).toBeUndefined();
    });
  });
});
