import { Component, input, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { NotesUploadDialog } from '../notes-upload/notes-upload.component';
import { MeetingNote } from '../../../models/meeting-note.model';

@Component({
  selector: 'app-notes-list',
  standalone: true,
  imports: [MatListModule, MatIconModule],
  templateUrl: './notes-list.component.html',
  styleUrl: './notes-list.component.scss'
})
export class NotesListComponent {
  notesMetadata = input<MeetingNote[]>([]);

  private router = inject(Router);
  private dialog = inject(MatDialog);

  goToNoteDetail(noteId: string) {
    // Assumes projectId is in the current route
    const url = this.router.url;
    const match = url.match(/projects\/(.+?)\//);
    const projectId = match ? match[1] : null;
    if (projectId) {
      this.router.navigate([`/projects/${projectId}/meetings/${noteId}`]);
    }
  }

  openUploadDialog() {
    this.dialog.open(NotesUploadDialog);
  }
}
