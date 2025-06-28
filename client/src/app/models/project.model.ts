import { User } from './user.model';
import { MeetingNote } from './meeting-note.model';
import { Report } from './report.model';

export interface Project {
  id: string;
  name: string;
  description: string;

  users: User[];
  meetingNotes: MeetingNote[];
  reports: Report[];
}
