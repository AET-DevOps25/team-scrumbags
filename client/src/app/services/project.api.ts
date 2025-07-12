import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../environment';
import { Project } from '../models/project.model';
import { User } from '../models/user.model';
import { handleError } from './api-utils';

@Injectable({
  providedIn: 'root',
})
export class ProjectApi {
  private http = inject(HttpClient);

  getProjectList(): Observable<Project[]> {
    return this.http
      .get<Project[]>(`${environment.projectManagementUrl}/projects`)
      .pipe(catchError(handleError('Error fetching project list')));
  }

  createProject(project: Project): Observable<Project> {
    return this.http
      .post<Project>(`${environment.projectManagementUrl}/projects`, project)
      .pipe(catchError(handleError('Error creating project')));
  }

  getProjectById(id: string): Observable<Project> {
    return this.http
      .get<Project>(`${environment.projectManagementUrl}/projects/${id}`)
      .pipe(
        catchError(handleError(`Error fetching project by ID (${id})`))
      );
  }

  getUsersInProject(projectId: string): Observable<User[]> {
    return this.http
      .get<User[]>(`${environment.projectManagementUrl}/projects/${projectId}/users`)
      .pipe(
        catchError(
          handleError(`Error fetching users in project (${projectId})`)
        )
      );
  }

  assignUserToProject(
    projectId: string,
    userIds: string[]
  ): Observable<string[]> {
    return this.http
      .post<string[]>(`${environment.projectManagementUrl}/projects/${projectId}/users`, userIds)
      .pipe(
        catchError(
          handleError(`Error assigning user to project (${projectId})`)
        )
      );
  }

  removeUserFromProject(
    projectId: string,
    userIds: string[]
  ): Observable<string[]> {
    return this.http
      .delete<string[]>(`${environment.projectManagementUrl}/projects/${projectId}/users`, {
        body: userIds,
      })
      .pipe(
        catchError(
          handleError(`Error removing user from project (${projectId})`)
        )
      );
  }
}
