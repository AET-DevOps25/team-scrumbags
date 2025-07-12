import { HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';

export function handleError(operation: string) {
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
