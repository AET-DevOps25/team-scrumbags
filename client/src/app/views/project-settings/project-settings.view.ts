import { Component, computed, inject } from '@angular/core';
import { Project } from '../../models/project.model';
import { ProjectState } from '../../states/project.state';
import { ProjectService } from '../../services/project.service';

@Component({
  selector: 'app-project-settings',
  imports: [],
  templateUrl: './project-settings.view.html',
  styleUrl: './project-settings.view.scss',
})
export class ProjectSettingsView {
  protected state = inject(ProjectState);
  private service = inject(ProjectService);

  project = computed<Project | null>(() => this.service.selectedProject());
}
