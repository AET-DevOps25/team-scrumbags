import { inject, Injectable } from '@angular/core';
import { finalize, Observable, tap } from 'rxjs';
import { User } from '../models/user.model';
import { MeetingNotesApi } from './meeting-notes.api';
import { MeetingNote } from '../models/meeting-note.model';
import { ProjectState } from '../states/project.state';

@Injectable({
  providedIn: 'root',
})
export class MeetingNotesService {
  private api = inject(MeetingNotesApi);
  private projectState = inject(ProjectState);

  constructor() {}

  public loadMeetingNotes(projectId: string): Observable<MeetingNote[]> {
    return this.api.getMeetingNotesMetadata(projectId).pipe(
      tap((meetingNotes) => {
        console.log('Meeting notes loaded:', meetingNotes);
        this.projectState.setMeetingNotes(projectId, meetingNotes);
      })
    );
  }
}
