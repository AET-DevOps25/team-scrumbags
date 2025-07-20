import { computed, inject, Injectable, signal, OnDestroy } from '@angular/core';
import { ProjectApi } from './project.api';
import { ProjectState } from '../states/project.state';
import { filter, finalize, Observable, tap, takeUntil, Subject } from 'rxjs';
import { Project } from '../models/project.model';
import { NavigationEnd, Router } from '@angular/router';
import Keycloak from 'keycloak-js';
import { User } from '../models/user.model';

@Injectable({
  providedIn: 'root',
})
export class ProjectService implements OnDestroy {
  private api = inject(ProjectApi);
  private state = inject(ProjectState);
  private router = inject(Router);

  private keycloak = inject(Keycloak);
  private destroy$ = new Subject<void>();

  private _isLoadingProjectList = signal<boolean>(false);
  public isLoadingProjectList = this._isLoadingProjectList.asReadonly();

  private readonly _selectedProjectId = signal<string | undefined>(undefined);
  public readonly selectedProjectId = this._selectedProjectId.asReadonly();
  public selectedProject = computed<Project | undefined>(() => {
    const projectId = this._selectedProjectId();
    return projectId ? this.state.allProjects().get(projectId) ?? undefined : undefined;
  });

  constructor() {
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntil(this.destroy$)
      )
      .subscribe(() => {
        const url = this.router.url;
        const match = url.match(/^\/projects\/([^/]+)/);
        const projectId = match ? match[1] : undefined;
        this.selectProject(projectId).subscribe();
      });
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
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
        this.state.updateProject(projectId, project);
      })
    );
  }

  private selectProject(projectId: string | undefined): Observable<Project | undefined> {
    if (!projectId) {
      this._selectedProjectId.set(undefined);
      return new Observable<undefined>((observer) => {
        observer.next(undefined);
        observer.complete();
      });
    }

    return this.loadProject(projectId).pipe(
      tap((project) => {
        this._selectedProjectId.set(project.id);
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

  public loadUsersOfProject(projectId: string) {
    return this.api.getUsersInProject(projectId).pipe(
      tap((user) => {
        this.state.setUsersOfProject(projectId, user);
      })
    );
  }

  public assignUserToProject(projectId: string, users: User[]) {
    const userIds = users.map((u) => u.id);
    return this.api.assignUserToProject(projectId, userIds).pipe(
      tap((assignedUserIds) => {
        // Update the state by adding users to the project
        assignedUserIds.forEach((userId) => {
          const user = users.find((u) => u.id === userId);
          if (!user) {
            return; // continue in for each
          }
          this.state.addUserToProject(projectId, user);
        });
      })
    );
  }

  public removeUserFromProject(projectId: string, userIds: string[]) {
    return this.api.removeUserFromProject(projectId, userIds).pipe(
      tap(() => {
        // Update the state by removing users from the project
        userIds.forEach((userId) => {
          this.state.removeUserFromProject(projectId, userId);
        });
      })
    );
  }
}
