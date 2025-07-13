import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../environment';
import { handleError } from './api-utils';
import { TranscriptionUserMapping } from '../models/transcription-users.model';

@Injectable({
  providedIn: 'root',
})
export class TranscriptionApi {
  private http = inject(HttpClient);
  private baseUrl = `${environment.meetingNotesUrl}/projects`;

  getSpeakers(projectId: string): Observable<TranscriptionUserMapping[]> {
    return this.http
      .get<TranscriptionUserMapping[]>(`${this.baseUrl}/${projectId}/speakers`)
      .pipe(
        catchError((error) => {
          if (error.status === 204) {
            return of([]);
          }
          return handleError('Error loading voice samples')(error);
        })
      );
  }

  getVoiceSample(projectId: string, userId: string): Observable<Blob> {
    return this.http
      .get(`${this.baseUrl}/${projectId}/speakers/${userId}/sample`, {
        responseType: 'blob',
      })
      .pipe(catchError(handleError('Error fetching voice sample')));
  }

  createSpeaker(
    projectId: string,
    userId: string,
    userName: string,
    file: File
  ): Observable<TranscriptionUserMapping> {
    const formData = new FormData();
    formData.append('userName', userName);
    formData.append('speakingSample', file, file.name);

    return this.http
      .post<TranscriptionUserMapping>(
        `${this.baseUrl}/${projectId}/speakers/${userId}`,
        formData
      )
      .pipe(
        catchError((error: HttpErrorResponse) => {
          let message = 'Failed to create speaker';
          if (error.error && typeof error.error === 'string') {
            message = error.error;
          } else if (error.error?.message) {
            message = error.error.message;
          } else if (error.message) {
            message = error.message;
          }
          return throwError(() => new Error(message));
        })
      );
  }

  updateSpeaker(
    projectId: string,
    userId: string,
    userName: string,
    file: File
  ): Observable<TranscriptionUserMapping> {
    const formData = new FormData();
    formData.append('userName', userName);
    formData.append('speakingSample', file, file.name);

    return this.http
      .put<TranscriptionUserMapping>(
        `${this.baseUrl}/${projectId}/speakers/${userId}`,
        formData
      )
      .pipe(
        catchError((error: HttpErrorResponse) => {
          let message = 'Failed to update speaker';
          if (error.error && typeof error.error === 'string') {
            message = error.error;
          } else if (error.error?.message) {
            message = error.error.message;
          } else if (error.message) {
            message = error.message;
          }
          return throwError(() => new Error(message));
        })
      );
  }

  deleteVoiceSample(projectId: string, userId: string): Observable<void> {
    return this.http
      .delete<void>(`${this.baseUrl}/${projectId}/speakers/${userId}`)
      .pipe(catchError(handleError('Error deleting voice sample')));
  }
}
