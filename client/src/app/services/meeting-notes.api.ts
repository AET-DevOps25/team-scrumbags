import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../environment';
import { MeetingNote } from '../models/meeting-note.model';
import { handleError } from './api-utils';

@Injectable({
  providedIn: 'root',
})
export class MeetingNotesApi {
  private http = inject(HttpClient);

  getMeetingNotesMetadata(projectId: string): Observable<MeetingNote[]> {
    return this.http
      .get<MeetingNote[]>(
        `${environment.meetingNotesUrl}/projects/${projectId}/transcripts`
      )
      .pipe(catchError(handleError('Error fetching transcription list')));
  }

  getMeetingNote(projectId: string, noteId: string): Observable<MeetingNote> {
    return this.http
      .get<MeetingNote>(
        `${environment.meetingNotesUrl}/projects/${projectId}/transcripts/${noteId}`
      )
      .pipe(catchError(handleError('Error fetching meeting note')));
  }

  uploadMeetingNoteFile(
    projectId: string,
    speakerAmount: number,
    file: File
  ): Observable<MeetingNote> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http
      .post<MeetingNote>(
        `${environment.meetingNotesUrl}/projects/${projectId}/transcripts?speakerAmount=${speakerAmount}`,
        formData
      )
      .pipe(catchError(handleError('Error uploading meeting note')));
  }

  getMeetingNoteUrl(projectId: string, noteId: string): Observable<string> {
    return new Observable((observer) => {
      const url = `${environment.meetingNotesUrl}/projects/${projectId}/transcripts/${noteId}/audio`;
      observer.next(url);
      observer.complete();
    });
  }
}
