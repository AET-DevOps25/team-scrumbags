import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { environment } from '../environment';
import { Report } from '../models/report.model';
import { Message } from '../models/message.model';

@Injectable({
  providedIn: 'root',
})
export class ChatApi {
  private http = inject(HttpClient);

  getChatMessages(projectId: string): Observable<Message[]> {
    return this.http
      .get<Message[]>(`${environment.genAiUrl}/projects/${projectId}/chat`)
      .pipe(
        map((msg) => {
          for (const m of msg) {
            m.timestamp = new Date(m.timestamp);
          }
          return msg;
        }),
        catchError(this.handleError('Error fetching chat messages'))
      );
  }

  sendMessage(projectId: string, message: string): Observable<Message[]> {
    return this.http
      .post<Message[]>(`${environment.genAiUrl}/projects/${projectId}/chat`, {
        message: message,
      })
      .pipe(
        map((msg) => {
          for (const m of msg) {
            m.timestamp = new Date(m.timestamp);
          }
          return msg;
        }),
        catchError(this.handleError('Error sending message'))
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
