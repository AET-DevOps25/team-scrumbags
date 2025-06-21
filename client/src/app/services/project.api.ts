import { inject, Injectable, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { map, catchError, finalize } from 'rxjs/operators';
import { environment } from '../environment';
import { Project } from '../models/project.model';

@Injectable({
  providedIn: 'root',
})
export class ProjectApi {
  private readonly baseUrl = environment.apiUrl;
  private http = inject(HttpClient);


  private _isLoadingProjectList = signal<boolean>(false);
  public isLoadingProjectList = this._isLoadingProjectList.asReadonly();

  getProjectList(): Observable<Project[]> {
    this._isLoadingProjectList.set(true);
    return this.http.get<Project[]>(`${this.baseUrl}/projects`).pipe(
      catchError(this.handleError('Error fetching project list')),
      finalize(() => this._isLoadingProjectList.set(false))
    );
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
