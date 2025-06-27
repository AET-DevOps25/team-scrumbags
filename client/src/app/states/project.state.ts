import { inject, Injectable, signal } from '@angular/core';
import { Project } from '../models/project.model';
import { User } from '../models/user.model';
import { UserState } from './user.state';
import { MeetingNote } from '../models/meeting-note.model';

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
    this.updateProject(id, { meetingNotes: meetingNotes });
  }

  addMeetingNote(id: string, meetingNote: MeetingNote) {
    const project = this.allProjects().get(id);
    if (!project) {
      return;
    }

    let meetingNotes = project.meetingNotes || [];
    meetingNotes = [...meetingNotes, meetingNote];
    this.updateProject(id, { meetingNotes: meetingNotes });
  }
}
