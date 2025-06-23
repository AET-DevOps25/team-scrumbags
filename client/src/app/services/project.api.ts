import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../environment';
import { Project } from '../models/project.model';
import { User } from '../models/user.model';

@Injectable({
  providedIn: 'root',
})
export class ProjectApi {
  private readonly baseUrl = environment.apiUrl;
  private http = inject(HttpClient);

  getProjectList(): Observable<Project[]> {
    return this.http
      .get<Project[]>(`${this.baseUrl}/projects`)
      .pipe(catchError(this.handleError('Error fetching project list')));
  }

  createProject(project: Project): Observable<Project> {
    return this.http
      .post<Project>(`${this.baseUrl}/projects`, project)
      .pipe(catchError(this.handleError('Error creating project')));
  }

  getProjectById(id: string): Observable<Project> {
    return this.http
      .get<Project>(`${this.baseUrl}/projects/${id}`)
      .pipe(
        catchError(this.handleError(`Error fetching project by ID (${id})`))
      );
  }

  getUsersInProject(projectId: string): Observable<User[]> {
    return this.http
      .get<User[]>(`${this.baseUrl}/projects/${projectId}/users`)
      .pipe(
        catchError(
          this.handleError(`Error fetching users in project (${projectId})`)
        )
      );
  }

  assignUserToProject(
    projectId: string,
    userIds: string[]
  ): Observable<string[]> {
    return this.http
      .post<string[]>(`${this.baseUrl}/projects/${projectId}/users`, userIds)
      .pipe(
        catchError(
          this.handleError(`Error assigning user to project (${projectId})`)
        )
      );
  }

  removeUserFromProject(
    projectId: string,
    userIds: string[]
  ): Observable<string[]> {
    return this.http
      .delete<string[]>(`${this.baseUrl}/projects/${projectId}/users`, {
        body: userIds,
      })
      .pipe(
        catchError(
          this.handleError(`Error assigning user to project (${projectId})`)
        )
      );
  }

  private handleError(operation: string) {
    return (error: HttpErrorResponse): Observable<never> => {
      console.error(`${operation}:`, error);

      let errorMessage = 'An unknown error occurred';
      if (error.error instanceof ErrorEvent) {
        // Client-side error
        errorMessage = `Client error: ${error.error.message}`;
      } else {
        // Server-side error
        errorMessage = `Server error: ${error.status} ${error.statusText}`;
      }

      return throwError(() => new Error(errorMessage));
    };
  }
}
