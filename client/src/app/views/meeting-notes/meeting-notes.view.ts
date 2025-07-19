import {
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { ProjectService } from '../../services/project.service';
import { MatButtonModule } from '@angular/material/button';
import { MeetingNotesService } from '../../services/meeting-notes.service';
import { MatDialog } from '@angular/material/dialog';
import { NotesUploadDialog } from '../../components/meeting-notes-upload/meeting-notes-upload.component';
import { MeetingNote } from '../../models/meeting-note.model';
import { CommonModule } from '@angular/common';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { catchError, EMPTY, finalize } from 'rxjs';
import { MatSnackBar } from '@angular/material/snack-bar';

@Component({
  selector: 'app-meeting-notes',
  imports: [
    CommonModule,
    MatButtonModule,
    MatToolbarModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './meeting-notes.view.html',
  styleUrl: './meeting-notes.view.scss',
})
export class MeetingNotesView {
  router = inject(Router);
  projectService = inject(ProjectService);
  meetingNotesService = inject(MeetingNotesService);
  private dialog = inject(MatDialog);
  private snackBar;

  prelookInput = input<number | undefined>(undefined);
  prelookCount = signal<number | undefined>(undefined);
  // flag thats true if
  displayMoreButton = computed(() => {
    const project = this.projectService.selectedProject();
    let notesMetadata: MeetingNote[] = [];
    if (project && project.meetingNotes) {
      notesMetadata = Array.from(project.meetingNotes.values());
    }
    const prelookInputValue = this.prelookInput();
    return prelookInputValue ? notesMetadata.length > prelookInputValue : false;
  });

  prelookMetadata = computed(() => {
    const end = this.prelookCount();
    const project = this.projectService.selectedProject();
    let notesMetadata: MeetingNote[] = [];
    if (project && project.meetingNotes) {
      notesMetadata = Array.from(project.meetingNotes.values());
    }

    return end ? notesMetadata.slice(0, end) : notesMetadata;
  });

  isLoading = signal(false);

  constructor() {
    this.snackBar = inject(MatSnackBar);
    effect(() => {
      const projectId = this.projectService.selectedProjectId();
      if (projectId) {
        this.isLoading.set(true);
        this.meetingNotesService
          .loadMeetingNotes(projectId)
          .pipe(
            catchError((error) => {
              this.snackBar.open(
                `Error loading meeting notes: ${error.message}`,
                'Close',
                { duration: 3000 }
              );
              return EMPTY;
            }),
            finalize(() => this.isLoading.set(false))
          )
          .subscribe();
      }

      this.prelookCount.set(this.prelookInput());
    });
  }

  viewAllNotes() {
    this.prelookCount.set(undefined);
  }

  viewLess() {
    this.prelookCount.set(this.prelookInput());
  }

  openFile(note: MeetingNote) {
    const projectId = this.projectService.selectedProjectId();
    if (!projectId) {
      console.error('No project selected for downloading meeting note file');
      return;
    }

    const url = `${window.location.origin}/projects/${projectId}/meetings/${note.id}`;
    window.open(url, '_blank');
  }

  openUploadDialog() {
    this.dialog.open(NotesUploadDialog);
  }
}
