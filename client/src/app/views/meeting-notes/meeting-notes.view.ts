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

  prelookInput = input<number | undefined>(undefined, {
    alias: 'prelookCount',
  });
  prelookCount = signal<number | undefined>(undefined);
  // flag thats true if 
  displayMoreButton = computed(() => {
    const notesMetadata =
      this.projectService.selectedProject()?.meetingNotes ?? [];
    const prelookInputValue = this.prelookInput();
    return prelookInputValue ? notesMetadata.length > prelookInputValue : false;
  });

  prelookMetadata = computed(() => {
    const end = this.prelookCount();
    const notesMetadata =
      this.projectService.selectedProject()?.meetingNotes ?? [];
    return end ? notesMetadata.slice(0, end) : notesMetadata;
  });

  constructor() {
    effect(() => {
      const projectId = this.projectService.selectedProjectId();
      if (projectId) {
        this.meetingNotesService.loadMeetingNotes(projectId).subscribe();
      }

      this.prelookCount.set(this.prelookInput());
      console.log(this.prelookInput());
    });
  }

  viewAllNotes() {
    this.prelookCount.set(undefined);
  }

  viewLess() {
    this.prelookCount.set(this.prelookInput());
  }
}
