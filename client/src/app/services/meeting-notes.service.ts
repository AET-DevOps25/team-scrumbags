import { inject, Injectable } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { MeetingNotesApi } from './meeting-notes.api';
import { MeetingNote } from '../models/meeting-note.model';
import { ProjectState } from '../states/project.state';

@Injectable({
  providedIn: 'root',
})
export class MeetingNotesService {
  private api = inject(MeetingNotesApi);
  private projectState = inject(ProjectState);

  public loadMeetingNotes(projectId: string): Observable<MeetingNote[]> {
    return this.api.getMeetingNotesMetadata(projectId).pipe(
      tap((meetingNotes) => {
        this.projectState.setMeetingNotes(projectId, meetingNotes);
      })
    );
  }

  public uploadMeetingNoteFile(
    projectId: string,
    file: File
  ): Observable<MeetingNote> {
    return this.api.uploadMeetingNoteFile(projectId, 1, file).pipe(
      tap((meetingNote) => {
        this.projectState.addMeetingNote(projectId, meetingNote);
      })
    );
  }
}
