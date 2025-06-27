import { Component, OnInit, signal } from '@angular/core';
import { NotesListComponent } from '../../components/meeting-notes/notes-list/notes-list.component';
import { MeetingNote } from '../../models/meeting-note.model';

@Component({
  selector: 'app-meeting-notes-all',
  imports: [
    NotesListComponent
  ],
  templateUrl: './meeting-notes-all.view.html',
  styleUrl: './meeting-notes-all.view.scss'
})
export class MeetingNotesAllView implements OnInit{

  notesMetadata = signal<MeetingNote[]>([])

  ngOnInit(): void {
    throw new Error('Method not implemented.');
  }

}
