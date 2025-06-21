import { computed, inject, Injectable, signal } from '@angular/core';
import { ProjectApi } from './project.api';
import { ProjectState } from '../states/project.state';
import { filter, finalize, Observable } from 'rxjs';
import { Project } from '../models/project.model';
import { NavigationEnd, Router } from '@angular/router';

@Injectable({
  providedIn: 'root',
})
export class ProjectService {
  private api = inject(ProjectApi);
  private state = inject(ProjectState);
  private router = inject(Router);

  private _isLoadingProjectList = signal<boolean>(false);
  public isLoadingProjectList = this._isLoadingProjectList.asReadonly();

  private readonly _selectedProjectId = signal<string | null>(null);
  public readonly selectedProjectId = this._selectedProjectId.asReadonly();
  public selectedProject = computed<Project | null>(() => {
    const projectId = this._selectedProjectId();
    return projectId ? this.state.findProjectById(projectId) : null;
  });

  constructor() {
    this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe(() => {
        const url = this.router.url;
        const match = url.match(/^\/projects\/([^/]+)/);
        const projectId = match ? match[1] : null;
        this.selectProject(projectId);
      });
  }

  public loadProjectList(): Observable<Project[]> {
    this._isLoadingProjectList.set(true);
    const observable = this.api
      .getProjectList()
      .pipe(finalize(() => this._isLoadingProjectList.set(false)));

    observable.subscribe({
      next: (projectList) => {
        console.log('Project list loaded:', projectList);
        this.state.setProjectList(projectList);
      },
      error: (error) => {
        console.error('Error loading project list:', error);
      },
    });
    return observable;
  }

  private loadProject(projectId: string): Observable<Project> {
    const observable = this.api.getProjectById(projectId);
    observable.subscribe({
      next: (project) => {
        console.log(project);
        this.state.setProjectById(projectId, project);
      },
      error: (error) => {
        console.error('Error loading project list:', error);
      },
    });
    return observable;
  }

  private selectProject(projectId: string | null): Observable<Project | null> {
    if (!projectId) {
      this._selectedProjectId.set(null);
      return new Observable<null>((observer) => {
        observer.next(null);
        observer.complete();
      });
    }

    const observable = this.loadProject(projectId);

    this.loadProject(projectId).subscribe({
      next: () => {
        this._selectedProjectId.set(projectId);
      },
    });

    return observable;
  }

  public createProject(project: Project): Observable<Project> {
    const observable = this.api.createProject(project);
    observable.subscribe({
      next: (newProject) => {
        this.state.setProjectById(newProject.id, newProject);
        console.log('Project created:', newProject);
      },
      error: (error) => {
        console.error('Error creating project:', error);
      },
    });
    return observable;
  }
}
