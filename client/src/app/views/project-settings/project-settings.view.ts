import { Component, computed, inject } from '@angular/core';
import { Project } from '../../models/project.model';
import { ProjectState } from '../../states/project.state';
import { ProjectService } from '../../services/project.service';
import { ProjectPeopleComponent } from '../../components/project-people.component/project-people.component';

@Component({
  selector: 'app-project-settings',
  imports: [
    ProjectPeopleComponent
  ],
  templateUrl: './project-settings.view.html',
  styleUrl: './project-settings.view.scss',
})
export class ProjectSettingsView {
  protected state = inject(ProjectState);
  private service = inject(ProjectService);

  project = computed<Project | null>(() => this.service.selectedProject());
}
