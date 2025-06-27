import { Component, inject, OnInit } from '@angular/core';
import { MeetingNotesApi } from '../../services/meeting-notes.api';
import { ActivatedRoute, Router } from '@angular/router';

@Component({
  selector: 'app-meeting-notes-detail',
  templateUrl: './meeting-notes-detail.view.html',
  styleUrl: './meeting-notes-detail.view.scss',
})
export class MeetingNotesDetailView implements OnInit {
  private meetingNoteApi = inject(MeetingNotesApi);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  ngOnInit(): void {
    const projectId = this.route.snapshot.paramMap.get('projectId');
    const noteId = this.route.snapshot.paramMap.get('meetingId');
    if (!projectId || !noteId) {
      console.error('Project ID or Note ID is missing');
      this.router.navigate(['/']);
      return;
    }

    this.meetingNoteApi.getMeetingNoteUrl(projectId, noteId).subscribe({
      next: (url) => {
        window.location.href = url;
      },
    });
  }
}
