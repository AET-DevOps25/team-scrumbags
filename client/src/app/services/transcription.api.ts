import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
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
          handleError('Error fetching speakers')(error);
          return of([]); // Return an empty array on error
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

  assignVoiceSample(
    projectId: string,
    userId: string,
    userName: string,
    file: File
  ): Observable<TranscriptionUserMapping> {
    const formData = new FormData();
    formData.append('userName', userName);
    formData.append('speakingSample', file, file.name);

    // This implementation uses a PUT/POST pattern. It first tries to update.
    // If the user (speaker) does not exist (404), it then creates a new one.
    return this.http
      .put<TranscriptionUserMapping>(
        `${this.baseUrl}/${projectId}/speakers/${userId}`,
        formData
      )
      .pipe(
        catchError((error) => {
          if (error.status === 404) {
            // Speaker not found, so create a new one
            return this.http.post<TranscriptionUserMapping>(
              `${this.baseUrl}/${projectId}/speakers/${userId}`,
              formData
            );
          }
          // For other errors, re-throw
          return handleError('Error assigning voice sample')(error);
        })
      );
  }

  deleteVoiceSample(projectId: string, userId: string): Observable<any> {
    return this.http
      .delete(`${this.baseUrl}/${projectId}/speakers/${userId}`)
      .pipe(catchError(handleError('Error deleting voice sample')));
  }
}
