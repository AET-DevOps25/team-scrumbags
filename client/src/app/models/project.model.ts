import { User } from './user.model';
import { MeetingNote } from './meeting-note.model';
import { Report } from './report.model';
import { Message } from './message.model';


export interface Project {
  id: string;
  name: string;
  description: string;

  users: User[];
  meetingNotes: Map<string, MeetingNote>;
  reports: Map<string, Report>;
  messages: Map<string, Message>;
}
