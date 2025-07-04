import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MeetingNotesService } from '../../services/meeting-notes.service';
import { ProjectService } from '../../services/project.service';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, finalize, tap } from 'rxjs';
import { MatInputModule } from '@angular/material/input';

@Component({
  selector: 'app-meeting-notes-upload',
  imports: [CommonModule, MatDialogModule, MatInputModule, MatButtonModule],
  templateUrl: './meeting-notes-upload.component.html',
  styleUrl: './meeting-notes-upload.component.scss',
})
export class NotesUploadDialog {
  private projectService = inject(ProjectService);
  private meetingNoteService = inject(MeetingNotesService);
  private dialogRef = inject(MatDialogRef<NotesUploadDialog>);
  private snackBar = inject(MatSnackBar);

  isSubmitting = signal(false);
  selectedFile = signal<File | null>(null);

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.selectedFile.set(input.files[0]);
    } else {
      this.selectedFile.set(null);
    }
  }

  onCancel() {
    this.dialogRef.close();
  }

  onSubmit() {
    const projectId = this.projectService.selectedProjectId();
    const file = this.selectedFile();
    if (!projectId) {
      console.error('No project selected for uploading meeting notes');
      return;
    }
    if (!file) {
      console.error('No file selected for uploading meeting notes');
      return;
    }

    this.isSubmitting.set(true);
    this.meetingNoteService
      .uploadMeetingNoteFile(projectId, file)
      .pipe(
        tap(() => this.dialogRef.close()),
        catchError((error) => {
          this.snackBar.open(
            `Error uploading meeting notes. Please try again. ${error.message}`,
            'Close',
            { duration: 3000 }
          );
          return [];
        }),
        finalize(() => {
          this.isSubmitting.set(false);
        })
      )
      .subscribe();
  }
}
