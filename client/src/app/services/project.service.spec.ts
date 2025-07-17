import { TestBed } from '@angular/core/testing';
import { Router, NavigationEnd } from '@angular/router';
import { of, Subject } from 'rxjs';
import { ProjectService } from './project.service';
import { ProjectApi } from './project.api';
import { ProjectState } from '../states/project.state';
import { Project } from '../models/project.model';
import { User } from '../models/user.model';
import Keycloak from 'keycloak-js';

describe('ProjectService', () => {
  let service: ProjectService;
  let projectApiSpy: jasmine.SpyObj<ProjectApi>;
  let projectStateSpy: jasmine.SpyObj<ProjectState>;
  let routerSpy: jasmine.SpyObj<Router>;
  let keycloakSpy: jasmine.SpyObj<Keycloak>;
  let routerEventsSubject: Subject<NavigationEnd>;

  const mockProject: Project = {
    id: 'project-1',
    name: 'Test Project',
    description: 'Test Description',
    users: [],
    meetingNotes: new Map(),
    reports: new Map(),
    messages: new Map()
  };

  const mockProjects: Project[] = [
    mockProject,
    { 
      id: 'project-2', 
      name: 'Project 2', 
      description: 'Description 2',
      users: [],
      meetingNotes: new Map(),
      reports: new Map(),
      messages: new Map()
    }
  ];

  const mockUsers: User[] = [
    { id: 'user-1', username: 'user1', email: 'user1@example.com' },
    { id: 'user-2', username: 'user2', email: 'user2@example.com' }
  ];

  beforeEach(() => {
    routerEventsSubject = new Subject<NavigationEnd>();
    
    const projectApiSpyObj = jasmine.createSpyObj('ProjectApi', [
      'getProjectList',
      'getProjectById',
      'createProject',
      'getUsersInProject',
      'assignUserToProject',
      'removeUserFromProject'
    ]);
    
    const projectStateSpyObj = jasmine.createSpyObj('ProjectState', [
      'setProjectList',
      'updateProject',
      'setProjectById',
      'setUsersOfProject',
      'addUserToProject',
      'removeUserFromProject'
    ]);
    
    const routerSpyObj = jasmine.createSpyObj('Router', ['navigate'], {
      events: routerEventsSubject.asObservable(),
      url: '/projects/project-1'
    });
    
    const keycloakSpyObj = jasmine.createSpyObj('Keycloak', ['updateToken']);

    // Mock the state allProjects signal with proper typing
    (projectStateSpyObj as unknown as { allProjects: jasmine.Spy }).allProjects = jasmine.createSpy().and.returnValue(
      new Map([['project-1', mockProject]])
    );

    TestBed.configureTestingModule({
      providers: [
        ProjectService,
        { provide: ProjectApi, useValue: projectApiSpyObj },
        { provide: ProjectState, useValue: projectStateSpyObj },
        { provide: Router, useValue: routerSpyObj },
        { provide: Keycloak, useValue: keycloakSpyObj }
      ]
    });

    service = TestBed.inject(ProjectService);
    projectApiSpy = TestBed.inject(ProjectApi) as jasmine.SpyObj<ProjectApi>;
    projectStateSpy = TestBed.inject(ProjectState) as jasmine.SpyObj<ProjectState>;
    routerSpy = TestBed.inject(Router) as jasmine.SpyObj<Router>;
    keycloakSpy = TestBed.inject(Keycloak) as jasmine.SpyObj<Keycloak>;
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('constructor', () => {
    it('should subscribe to router events and extract project ID', () => {
      projectApiSpy.getProjectById.and.returnValue(of(mockProject));
      
      // Trigger a NavigationEnd event
      routerEventsSubject.next(new NavigationEnd(1, '/projects/project-1', '/projects/project-1'));
      
      expect(projectApiSpy.getProjectById).toHaveBeenCalledWith('project-1');
    });

    it('should handle URLs without project ID', () => {
      Object.defineProperty(routerSpy, 'url', { value: '/dashboard' });
      
      routerEventsSubject.next(new NavigationEnd(1, '/dashboard', '/dashboard'));
      
      expect(service.selectedProjectId()).toBeUndefined();
    });
  });

  describe('loadProjectList', () => {
    it('should load project list and update state', () => {
      projectApiSpy.getProjectList.and.returnValue(of(mockProjects));

      const result$ = service.loadProjectList();

      result$.subscribe(projects => {
        expect(projects).toEqual(mockProjects);
        expect(projectStateSpy.setProjectList).toHaveBeenCalledWith(mockProjects);
      });

      expect(projectApiSpy.getProjectList).toHaveBeenCalled();
    });

    it('should set loading state during API call', () => {
      projectApiSpy.getProjectList.and.returnValue(of(mockProjects));

      expect(service.isLoadingProjectList()).toBeFalse();

      const result$ = service.loadProjectList();
      expect(service.isLoadingProjectList()).toBeTrue();

      result$.subscribe(() => {
        expect(service.isLoadingProjectList()).toBeFalse();
      });
    });
  });

  describe('createProject', () => {
    it('should create project and update state', () => {
      const newProject: Project = { 
        id: 'new-id', 
        name: 'New Project', 
        description: 'New Description',
        users: [],
        meetingNotes: new Map(),
        reports: new Map(),
        messages: new Map()
      };
      projectApiSpy.createProject.and.returnValue(of(newProject));

      const result$ = service.createProject(mockProject);

      result$.subscribe(project => {
        expect(project).toEqual(newProject);
        expect(projectStateSpy.setProjectById).toHaveBeenCalledWith(newProject.id, newProject);
        expect(keycloakSpy.updateToken).toHaveBeenCalledWith(Number.MAX_VALUE);
      });

      expect(projectApiSpy.createProject).toHaveBeenCalledWith(mockProject);
    });
  });

  describe('loadUsersOfProject', () => {
    it('should load users and update state', () => {
      projectApiSpy.getUsersInProject.and.returnValue(of(mockUsers));

      const result$ = service.loadUsersOfProject('project-1');

      result$.subscribe(users => {
        expect(users).toEqual(mockUsers);
        expect(projectStateSpy.setUsersOfProject).toHaveBeenCalledWith('project-1', mockUsers);
      });

      expect(projectApiSpy.getUsersInProject).toHaveBeenCalledWith('project-1');
    });
  });

  describe('assignUserToProject', () => {
    it('should assign users and update state', () => {
      const userIds = ['user-1', 'user-2'];
      projectApiSpy.assignUserToProject.and.returnValue(of(userIds));

      const result$ = service.assignUserToProject('project-1', mockUsers);

      result$.subscribe(assignedIds => {
        expect(assignedIds).toEqual(userIds);
        expect(projectStateSpy.addUserToProject).toHaveBeenCalledTimes(2);
        expect(projectStateSpy.addUserToProject).toHaveBeenCalledWith('project-1', mockUsers[0]);
        expect(projectStateSpy.addUserToProject).toHaveBeenCalledWith('project-1', mockUsers[1]);
      });

      expect(projectApiSpy.assignUserToProject).toHaveBeenCalledWith('project-1', userIds);
    });

    it('should handle partial assignment results', () => {
      const partialUserIds = ['user-1']; // Only one user assigned successfully
      projectApiSpy.assignUserToProject.and.returnValue(of(partialUserIds));

      const result$ = service.assignUserToProject('project-1', mockUsers);

      result$.subscribe(() => {
        expect(projectStateSpy.addUserToProject).toHaveBeenCalledTimes(1);
        expect(projectStateSpy.addUserToProject).toHaveBeenCalledWith('project-1', mockUsers[0]);
      });
    });
  });

  describe('removeUserFromProject', () => {
    it('should remove users and update state', () => {
      const userIds = ['user-1', 'user-2'];
      projectApiSpy.removeUserFromProject.and.returnValue(of(userIds));

      const result$ = service.removeUserFromProject('project-1', userIds);

      result$.subscribe(() => {
        expect(projectStateSpy.removeUserFromProject).toHaveBeenCalledTimes(2);
        expect(projectStateSpy.removeUserFromProject).toHaveBeenCalledWith('project-1', 'user-1');
        expect(projectStateSpy.removeUserFromProject).toHaveBeenCalledWith('project-1', 'user-2');
      });

      expect(projectApiSpy.removeUserFromProject).toHaveBeenCalledWith('project-1', userIds);
    });
  });

  describe('selectedProject computed', () => {
    it('should return selected project from state', () => {
      // The selectedProject computed should return the project from state
      const project = service.selectedProject();
      expect(project).toEqual(mockProject);
    });

    it('should return undefined when no project is selected', () => {
      // Create a new service instance with empty state for this test
      const emptyStateSpyObj = jasmine.createSpyObj('ProjectState', [
        'setProjectList',
        'updateProject', 
        'setProjectById',
        'setUsersOfProject',
        'addUserToProject',
        'removeUserFromProject'
      ]);
      emptyStateSpyObj.allProjects = jasmine.createSpy().and.returnValue(new Map());

      // Create new service with empty state
      TestBed.overrideProvider(ProjectState, { useValue: emptyStateSpyObj });
      const newService = TestBed.inject(ProjectService);
      
      const project = newService.selectedProject();
      expect(project).toBeUndefined();
    });
  });
});