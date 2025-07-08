import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../environment';
import { User } from '../models/user.model';

@Injectable({
  providedIn: 'root',
})
export class UserApi {
  private http = inject(HttpClient);

  getAllUsers(): Observable<User[]> {
    return this.http
      .get<User[]>(`${environment.projectManagementUrl}/users`)
      .pipe(catchError(this.handleError('Error fetching project list')));
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
