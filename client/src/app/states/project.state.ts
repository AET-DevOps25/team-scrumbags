import { Injectable, signal } from '@angular/core';
import { Project } from '../models/project.model';
import { User } from '../models/user.model';
import { MeetingNote } from '../models/meeting-note.model';
import { Report } from '../models/report.model';
import { Message } from '../models/message.model';

@Injectable({
  providedIn: 'root',
})
export class ProjectState {
  private readonly _allProjects = signal<Map<string, Project>>(new Map());

  readonly allProjects = this._allProjects.asReadonly();

  setProjectList(projectList: Project[]): void {
    const map = new Map<string, Project>();
    for (const project of projectList) {
      map.set(project.id, project);
    }
    this._allProjects.set(map);
  }

  setProjectById(id: string, project: Project): void {
    const map = new Map(this._allProjects());
    map.set(id, project);
    this._allProjects.set(map);
  }

  updateProject(id: string, project: Partial<Project>): void {
    const existingProject = this.allProjects().get(id);
    if (!existingProject) {
      return;
    }

    const updatedProject = { ...existingProject, ...project };
    this.setProjectById(id, updatedProject);
  }

  setUsersOfProject(id: string, users: User[]) {
    this.updateProject(id, { users: users });
  }

  addUserToProject(id: string, user: User) {
    const project = this.allProjects().get(id);
    if (!project) {
      return;
    }

    let users = project.users || [];

    users = [...users, user];
    this.updateProject(id, { users: users });
  }

  removeUserFromProject(id: string, userId: string) {
    const project = this.allProjects().get(id);
    if (!project) {
      return;
    }

    const users = project.users.filter((u) => u.id !== userId);
    this.updateProject(id, { users: users });
  }

  setMeetingNotes(id: string, meetingNotes: MeetingNote[]) {
    const meetingNotesMap = new Map(
      meetingNotes.map((meetingNote) => [meetingNote.id, meetingNote])
    );

    this.updateProject(id, { meetingNotes: meetingNotesMap });
  }

  updateMeetingNote(id: string, meetingNote: MeetingNote) {
    const project = this.allProjects().get(id);
    if (!project) {
      return;
    }

    const meetingNotes = project.meetingNotes || new Map<string, Report>();
    meetingNotes.set(meetingNote.id, meetingNote);

    this.updateProject(id, { meetingNotes: meetingNotes });
  }

  setReports(id: string, reports: Report[]) {
    const reportMap = new Map(reports.map((report) => [report.id, report]));

    this.updateProject(id, { reports: reportMap });
  }

  updateReport(id: string, report: Report) {
    const project = this.allProjects().get(id);
    if (!project) {
      return;
    }

    const reports = project.reports || new Map<string, Report>();
    reports.set(report.id, report);

    this.updateProject(id, { reports: reports });
  }

  setMessages(id: string, messages: Message[]) {
    const messageMap = new Map(messages.map((msg) => [msg.id, msg]));
    this.updateProject(id, { messages: messageMap });
  }

  addMessages(id: string, messages: Message[]) {
    const project = this.allProjects().get(id);
    if (!project) {
      return;
    }

    const projectMessages = project.messages || new Map<string, Message>();
    for (const message of messages) {
      projectMessages.set(message.id, message);
    }

    this.updateProject(id, { messages: projectMessages });
  }
}
