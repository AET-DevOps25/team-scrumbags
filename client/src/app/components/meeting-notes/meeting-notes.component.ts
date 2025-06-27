import { Component, computed, inject, signal } from '@angular/core';
import { MeetingNote } from '../../models/meeting-note.model';
import { Router } from '@angular/router';
import { ProjectService } from '../../services/project.service';
import { NotesListComponent } from './notes-list/notes-list.component';

@Component({
  selector: 'app-meeting-notes',
  imports: [
    NotesListComponent
  ],
  templateUrl: './meeting-notes.component.html',
  styleUrl: './meeting-notes.component.scss'
})
export class MeetingNotesComponent {
  router = inject(Router)
  projectService = inject(ProjectService)

  prelookCount = 3
  private notesMetadata = signal<MeetingNote[]>([]);
  prelookMetadata = computed(() => this.notesMetadata().slice(0, this.prelookCount))

  viewAllNotes(){
      this.router.navigate([`/projects/${this.projectService.selectedProjectId()}/meetings`]);
  }
}
