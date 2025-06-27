import {
  Component,
  computed,
  effect,
  inject,
  input,
} from '@angular/core';
import { Router } from '@angular/router';
import { ProjectService } from '../../services/project.service';
import { MatButtonModule } from '@angular/material/button';
import { MeetingNotesService } from '../../services/meeting-notes.service';
import { NotesListComponent } from '../../components/meeting-notes/notes-list/notes-list.component';

@Component({
  selector: 'app-meeting-notes',
  imports: [NotesListComponent, MatButtonModule],
  templateUrl: './meeting-notes.view.html',
  styleUrl: './meeting-notes.view.scss',
})
export class MeetingNotesView {
  router = inject(Router);
  projectService = inject(ProjectService);
  meetingNotesService = inject(MeetingNotesService);

  prelookCount = input<number | undefined>(undefined);
  prelookMetadata = computed(() => {
    const end = this.prelookCount();
    const notesMetadata = this.projectService.selectedProject()?.meetingNotes ?? [];
    console.log('Prelook metadata:', notesMetadata, 'End:', end);
    return end ? notesMetadata.slice(0, end) : notesMetadata;
  });

  constructor() {
    effect(() => {
      const projectId = this.projectService.selectedProjectId();
      if (projectId) {
        this.meetingNotesService.loadMeetingNotes(projectId).subscribe();
      }
    });
  }

  viewAllNotes() {
    this.router.navigate([
      `/projects/${this.projectService.selectedProjectId()}/meetings`,
    ]);
  }
}
