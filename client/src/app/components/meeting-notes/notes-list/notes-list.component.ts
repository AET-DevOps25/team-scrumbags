import { Component, input, inject } from '@angular/core';
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
  private dialog = inject(MatDialog);
  private projectService = inject(ProjectService);

  notesMetadata = input.required<MeetingNote[]>();

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
