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
        for (const note of meetingNotes) {
          // trigger polling for each note that is still loading
          if (note.loading) {
            this.pollMeetingNote(projectId, note.id);
          }

          if (!note.name || note.name === '') {
            note.name = 'Note ' + note.id;
          }
        }

        this.projectState.setMeetingNotes(projectId, meetingNotes);
      })
    );
  }

  public uploadMeetingNoteFile(
    projectId: string,
    speakerAmount: number,
    file: File
  ): Observable<MeetingNote> {
    return this.api.uploadMeetingNoteFile(projectId, speakerAmount, file).pipe(
      tap((meetingNote) => {
        console.log('Meeting note uploaded:', meetingNote);
        if (!meetingNote.name || meetingNote.name === '') {
          meetingNote.name = 'Note ' + meetingNote.id;
        }
        this.projectState.updateMeetingNote(projectId, meetingNote);

        // trigger polling for until the note has loading false
        if (meetingNote.loading) {
          this.pollMeetingNote(projectId, meetingNote.id);
        }
      })
    );
  }

  private async pollMeetingNote(projectId: string, noteId: string, count = 0) {
    if (count >= 10) {
      console.warn(`Polling stopped for note ${noteId} after 10 attempts.`);
      return;
    }

    // Poll the note every 5 seconds until it is no longer loading
    await new Promise((resolve) => setTimeout(resolve, 5000));

    this.api.getMeetingNote(projectId, noteId).subscribe((note) => {
      if (note.loading) {
        this.pollMeetingNote(projectId, noteId, count + 1);
        return;
      }

      if (!note.name || note.name === '') {
        note.name = 'Note ' + note.id;
      }
      this.projectState.updateMeetingNote(projectId, note);
    });
  }
}
