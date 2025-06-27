import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../environment';
import { MeetingNote } from '../models/meeting-note.model';

@Injectable({
  providedIn: 'root',
})
export class MeetingNotesApi {
  private http = inject(HttpClient);

  getMeetingNotesMetadata(projectId: string): Observable<MeetingNote[]> {
    return this.http
      .get<MeetingNote[]>(
        `${environment.meetingNotesUrl}/projects/${projectId}/meeting-notes`
      )
      .pipe(catchError(this.handleError('Error fetching project list')));
  }

  uploadMeetingNoteFile(
    projectId: string,
    file: File
  ): Observable<MeetingNote> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http
      .post<MeetingNote>(
        `${environment.meetingNotesUrl}/projects/${projectId}/meeting-notes`,
        formData
      )
      .pipe(catchError(this.handleError('Error uploading meeting note')));
  }

  getMeetingNoteFile(projectId: string, noteId: string): Observable<Blob> {
    return this.http
      .get(
        `${environment.meetingNotesUrl}/projects/${projectId}/meeting-notes/${noteId}/file`,
        {
          responseType: 'blob',
        }
      )
      .pipe(
        catchError(this.handleError('Error downloading meeting note file'))
      );
  }

  getMeetingNoteUrl(projectId: string, noteId: string): Observable<string> {
    return new Observable((observer) => {
      const url = `${environment.meetingNotesUrl}/projects/${projectId}/meeting-notes/${noteId}/file`;
      observer.next(url);
      observer.complete();
    });
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
