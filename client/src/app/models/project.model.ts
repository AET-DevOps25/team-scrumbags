import { User } from './user.model';
import { MeetingNote } from './meeting-note.model';

export interface Project {
  id: string;
  name: string;
  description: string;

  users: User[];
  meetingNotes: MeetingNote[];
}
