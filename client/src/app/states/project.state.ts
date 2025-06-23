import { inject, Injectable, signal } from '@angular/core';
import { Project } from '../models/project.model';
import { User } from '../models/user.model';
import { UserState } from './user.state';

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

  setUsersOfProject(id: string, users: User[]) {
    let project = this.allProjects().get(id);
    if (!project) {
      return;
    }

    project.users = users;
    this.setProjectById(id, { ...project });
  }

  addUserToProject(id: string, user: User) {
    let project = this.allProjects().get(id);
    if (!project) {
      return;
    }
    if (!project.users) {
      project.users = [];
    }

    project.users.push(user);
    this.setProjectById(id, { ...project });
  }

  removeUserFromProject(id: string, userId: string) {
    const project = this.allProjects().get(id);
    if (!project) {
      return;
    }
    project.users = project.users.filter((u) => u.id !== userId);
    this.setProjectById(id, { ...project });
  }
}
