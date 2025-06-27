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
        console.log('Meeting notes loaded:', meetingNotes);
        this.projectState.setMeetingNotes(projectId, meetingNotes);
      })
    );
  }

  public uploadMeetingNoteFile(
    projectId: string,
    file: File
  ): Observable<MeetingNote> {
    return this.api.uploadMeetingNoteFile(projectId, file).pipe(
      tap((meetingNote) => {
        console.log('Meeting note uploaded:', meetingNote);
        this.projectState.addMeetingNote(projectId, meetingNote);
      })
    );
  }
}
