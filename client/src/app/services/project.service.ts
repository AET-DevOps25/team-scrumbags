import { computed, inject, Injectable, signal } from '@angular/core';
import { ProjectApi } from './project.api';
import { ProjectState } from '../states/project.state';
import { filter, finalize, Observable, tap } from 'rxjs';
import { Project } from '../models/project.model';
import { NavigationEnd, Router } from '@angular/router';
import Keycloak from 'keycloak-js';

@Injectable({
  providedIn: 'root',
})
export class ProjectService {
  private api = inject(ProjectApi);
  private state = inject(ProjectState);
  private router = inject(Router);

  private keycloak = inject(Keycloak);

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
        this.selectProject(projectId).subscribe();
      });
  }

  public loadProjectList(): Observable<Project[]> {
    this._isLoadingProjectList.set(true);
    return this.api.getProjectList().pipe(
      tap((projectList) => {
        this.state.setProjectList(projectList);
      }),
      finalize(() => this._isLoadingProjectList.set(false))
    );
  }

  private loadProject(projectId: string): Observable<Project> {
    return this.api.getProjectById(projectId).pipe(
      tap((project) => {
        // Update the state with the loaded project
        this.state.setProjectById(projectId, project);
      })
    );
  }

  private selectProject(projectId: string | null): Observable<Project | null> {
    if (!projectId) {
      this._selectedProjectId.set(null);
      return new Observable<null>((observer) => {
        observer.next(null);
        observer.complete();
      });
    }

    return this.loadProject(projectId).pipe(
      tap((project) => {
        this._selectedProjectId.set(projectId);
      })
    );
  }

  public createProject(project: Project): Observable<Project> {
    return this.api.createProject(project).pipe(
      tap((newProject) => {
        // force refresh token to contain new project role
        this.keycloak.updateToken(Number.MAX_VALUE);
        // Update the state with the new project
        this.state.setProjectById(newProject.id, newProject);
      })
    );
  }
}
