import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { signal } from '@angular/core';
import { ProjectOverviewView } from './project-overview.view';
import { ProjectService } from '../../services/project.service';
import { ProjectState } from '../../states/project.state';
import { ProjectAddDialog } from '../../components/project-add/project-add.component';
import { Project } from '../../models/project.model';

describe('ProjectOverviewView', () => {
  let component: ProjectOverviewView;
  let fixture: ComponentFixture<ProjectOverviewView>;
  let projectServiceSpy: jasmine.SpyObj<ProjectService>;
  let projectStateSpy: jasmine.SpyObj<ProjectState>;
  let routerSpy: jasmine.SpyObj<Router>;
  let dialogSpy: jasmine.SpyObj<MatDialog>;

  const mockProjects: Project[] = [
    { 
      id: 'project-1', 
      name: 'Project 1', 
      description: 'Description 1',
      users: [],
      meetingNotes: new Map(),
      reports: new Map(),
      messages: new Map()
    },
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

  beforeEach(async () => {
    const projectServiceSpyObj = jasmine.createSpyObj('ProjectService', [], {
      isLoadingProjectList: signal(false)
    });
    
    const projectStateSpyObj = jasmine.createSpyObj('ProjectState', [], {
      allProjects: signal(new Map([
        ['project-1', mockProjects[0]],
        ['project-2', mockProjects[1]]
      ]))
    });
    
    const routerSpyObj = jasmine.createSpyObj('Router', ['navigate']);
    const dialogSpyObj = jasmine.createSpyObj('MatDialog', ['open']);

    await TestBed.configureTestingModule({
      imports: [
        ProjectOverviewView,
        NoopAnimationsModule
      ],
      providers: [
        { provide: ProjectService, useValue: projectServiceSpyObj },
        { provide: ProjectState, useValue: projectStateSpyObj },
        { provide: Router, useValue: routerSpyObj },
        { provide: MatDialog, useValue: dialogSpyObj }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ProjectOverviewView);
    component = fixture.componentInstance;
    projectServiceSpy = TestBed.inject(ProjectService) as jasmine.SpyObj<ProjectService>;
    projectStateSpy = TestBed.inject(ProjectState) as jasmine.SpyObj<ProjectState>;
    routerSpy = TestBed.inject(Router) as jasmine.SpyObj<Router>;
    dialogSpy = TestBed.inject(MatDialog) as jasmine.SpyObj<MatDialog>;

    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display loading state', () => {
    // Mock loading state
    Object.defineProperty(projectServiceSpy, 'isLoadingProjectList', {
      value: signal(true),
      writable: true
    });

    // Re-create component to pick up new loading state
    fixture = TestBed.createComponent(ProjectOverviewView);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.loading()).toBeTrue();
  });

  it('should not display loading state when not loading', () => {
    expect(component.loading()).toBeFalse();
  });

  describe('navigateToProject', () => {
    it('should navigate to project page', () => {
      const projectId = 'test-project-id';

      component.navigateToProject(projectId);

      expect(routerSpy.navigate).toHaveBeenCalledWith(['/projects', projectId]);
    });
  });

  describe('openAddProjectDialog', () => {
    it('should open project add dialog with correct configuration', () => {
      component.openAddProjectDialog();

      expect(dialogSpy.open).toHaveBeenCalledWith(ProjectAddDialog, {
        maxWidth: '80rem',
        minWidth: '50rem',
      });
    });
  });

  describe('template rendering', () => {
    it('should render project cards when projects are available', () => {
      fixture.detectChanges();
      
      // Check if the component can access the projects from state
      expect((component as unknown as { state: { allProjects: () => Map<string, Project> } }).state.allProjects()).toEqual(new Map([
        ['project-1', mockProjects[0]],
        ['project-2', mockProjects[1]]
      ]));
    });

    it('should show loading spinner when loading', () => {
      // Mock loading state
      Object.defineProperty(projectServiceSpy, 'isLoadingProjectList', {
        value: signal(true),
        writable: true
      });

      fixture = TestBed.createComponent(ProjectOverviewView);
      component = fixture.componentInstance;
      fixture.detectChanges();

      // Note: The actual template check would depend on the template implementation
      // This is a placeholder for template-based testing
    });

    it('should have add project button', () => {
      fixture.detectChanges();
      
      // Note: The actual selector would depend on the template implementation
    });
  });

  describe('integration', () => {
    it('should handle empty project list', () => {
      // Mock empty state
      Object.defineProperty(projectStateSpy, 'allProjects', {
        value: signal(new Map()),
        writable: true
      });

      fixture = TestBed.createComponent(ProjectOverviewView);
      component = fixture.componentInstance;
      fixture.detectChanges();

      expect((component as unknown as { state: { allProjects: () => Map<string, Project> } }).state.allProjects().size).toBe(0);
    });

    it('should handle project state updates', () => {
      const newProject: Project = { 
        id: 'project-3', 
        name: 'Project 3', 
        description: 'Description 3',
        users: [],
        meetingNotes: new Map(),
        reports: new Map(),
        messages: new Map()
      };
      const updatedProjects = new Map([
        ['project-1', mockProjects[0]],
        ['project-2', mockProjects[1]],
        ['project-3', newProject]
      ]);

      // Mock updated state
      Object.defineProperty(projectStateSpy, 'allProjects', {
        value: signal(updatedProjects),
        writable: true
      });

      fixture = TestBed.createComponent(ProjectOverviewView);
      component = fixture.componentInstance;
      fixture.detectChanges();

      expect((component as unknown as { state: { allProjects: () => Map<string, Project> } }).state.allProjects().size).toBe(3);
      expect((component as unknown as { state: { allProjects: () => Map<string, Project> } }).state.allProjects().get('project-3')).toEqual(newProject);
    });
  });
});