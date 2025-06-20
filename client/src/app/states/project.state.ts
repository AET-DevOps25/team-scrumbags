import { Injectable, signal } from '@angular/core';
import { Project } from '../models/project.model';

@Injectable({
  providedIn: 'root',
})
export class ProjectState {
  private readonly _projectList = signal<Project[]>([]);

  readonly projectList = this._projectList.asReadonly();

  setProjectList(projectList: Project[]): void {
    this._projectList.set(projectList);
  }

  findProjectById(id: number): Project | null {
    return this._projectList().find(project => project.id === id) || null;
  }

  setProjectById(id: number, project: Project): void {
    const currentList = this._projectList();
    const index = currentList.findIndex(p => p.id === id);
    if (index !== -1) {
      // if found, update the project
      currentList[index] = project;
      this._projectList.set([...currentList]);
    } else {
      // if not found, add the new project
      this._projectList.set([...currentList, project]);
    }
  }
}
