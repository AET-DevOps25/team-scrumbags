import { Component, input, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { NotesUploadDialog } from '../notes-upload/notes-upload.component';
import { MeetingNote } from '../../../models/meeting-note.model';
import { MatButtonModule } from '@angular/material/button';
import { CommonModule } from '@angular/common';
import { ProjectService } from '../../../services/project.service';

@Component({
  selector: 'app-notes-list',
  standalone: true,
  imports: [CommonModule, MatListModule, MatIconModule, MatButtonModule],
  templateUrl: './notes-list.component.html',
  styleUrl: './notes-list.component.scss',
})
export class NotesListComponent {
  private router = inject(Router);
  private dialog = inject(MatDialog);
  private projectService = inject(ProjectService);

  notesMetadata = input.required<MeetingNote[]>();

  goToNoteDetail(noteId: string) {
    const projectId = this.projectService.selectedProjectId();
    if (projectId) {
      this.router.navigate([`/projects/${projectId}/meetings/${noteId}`]);
    }
  }

  openUploadDialog() {
    this.dialog.open(NotesUploadDialog);
  }
}
