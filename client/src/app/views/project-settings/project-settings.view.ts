import { Component, computed, inject } from '@angular/core';
import { Project } from '../../models/project.model';
import { ProjectState } from '../../states/project.state';
import { ProjectService } from '../../services/project.service';
import { ProjectUserSettings } from '../../components/settings-project-users/settings-project-users.component';
import { SdlcSettings } from '../../components/settings-sdlc/settings-sdlc.component';
import { TranscriptionSettings } from '../../components/settings-transcription/settings-transcription.component';

@Component({
  selector: 'app-project-settings',
  imports: [
    ProjectUserSettings,
    SdlcSettings,
    TranscriptionSettings
  ],
  templateUrl: './project-settings.view.html',
  styleUrl: './project-settings.view.scss',
})
export class ProjectSettingsView {
  protected state = inject(ProjectState);
  private service = inject(ProjectService);

  project = computed<Project | undefined>(() => this.service.selectedProject());
}
