import { MeetingNote } from "./meeting-note.model";

export interface Project {
  id: string;
  name: string;
  description: string;

  meetingNotes: MeetingNote[];
}
