import { Component, inject, OnInit } from '@angular/core';
import { MeetingNotesApi } from '../../services/meeting-notes.api';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-meeting-notes-detail',
  templateUrl: './meeting-notes-detail.view.html',
  styleUrl: './meeting-notes-detail.view.scss',
  imports: [MatButtonModule],
})
export class MeetingNotesDetailView implements OnInit {
  private meetingNoteApi = inject(MeetingNotesApi);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  private projectId: string | null = null;
  private noteId: string | null = null;

  ngOnInit(): void {
    this.projectId = this.route.snapshot.paramMap.get('projectId');
    this.noteId = this.route.snapshot.paramMap.get('meetingId');

    this.openFile();
  }

  openFile() {
    if (!this.projectId || !this.noteId) {
      console.error('Project ID or Note ID is missing');
      this.router.navigate(['/']);
      return;
    }

    this.meetingNoteApi
      .getMeetingNoteUrl(this.projectId, this.noteId)
      .subscribe({
        next: (url) => {
          window.location.href = url;
        },
      });
  }
}
