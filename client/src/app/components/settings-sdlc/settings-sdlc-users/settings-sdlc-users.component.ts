import { Component, computed, effect, inject, signal } from '@angular/core';
import { User } from '../../../models/user.model';
import { ProjectService } from '../../../services/project.service';
import { SdlcUserMapping } from '../../../models/sdlc-users.model';
import { SdlcApi } from '../../../services/sdlc.api';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';

@Component({
  selector: 'app-settings-sdlc-users',
  imports: [
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './settings-sdlc-users.component.html',
  styleUrl: './settings-sdlc-users.component.scss',
})
export class SettingsSdlcUsersComponent {
  private projectService = inject(ProjectService);
  private sdlcApi = inject(SdlcApi);

  private projectsUser = computed<User[]>(() => {
    return this.projectService.selectedProject()?.users ?? [];
  });

  currentlyEditedMapping = signal<SdlcUserMapping | null>(null);
  private userMappings = signal<Map<string, SdlcUserMapping>>(new Map());
  userMappingMap = computed<{ user: User; mapping: SdlcUserMapping }[]>(() => {
    const users = this.projectsUser();
    const mappings = this.userMappings();
    const projectId = this.projectService.selectedProjectId();
    if (!projectId) {
      return [];
    }

    return users.map((user) => {
      const mapping = mappings.get(user.id) ?? {
        userId: user.id,
        platform: 'GITHUB',
        platformUserId: '',
        projectId: projectId,
      };
      return { user, mapping };
    });
  });

  isLoadingMappings = signal(false);
  isPostingMapping = signal(false);

  constructor() {
    effect(() => {
      const projectId = this.projectService.selectedProject()?.id;
      if (projectId) {
        this.isLoadingMappings.set(true);
        this.sdlcApi.getUserMappings(projectId).subscribe({
          next: (mappings) => {
            const map = new Map<string, SdlcUserMapping>();
            for (const mapping of mappings) {
              map.set(mapping.userId, mapping);
            }
            this.userMappings.set(map);
          },
          complete: () => {
            this.isLoadingMappings.set(false);
          },
        });
      }
    });
  }

  onCurrentlyEditedMappingChange(platformUserId: string) {
    const prevMapping = this.currentlyEditedMapping();
    if (!prevMapping) {
      return;
    }
    this.currentlyEditedMapping.set({
      ...prevMapping,
      platformUserId: platformUserId,
    });
  }

  onSubmitMapping() {
    const projectId = this.projectService.selectedProject()?.id;
    const submittedMapping = this.currentlyEditedMapping();
    if (!projectId || !submittedMapping) {
      return;
    }

    this.isPostingMapping.set(true);
    this.sdlcApi.saveUserMapping(projectId, submittedMapping).subscribe({
      next: (mapping) => {
        const currentMappings = this.userMappings();

        const newMappings = new Map(
          currentMappings.set(mapping.userId, mapping)
        );
        this.userMappings.set(newMappings);
        this.currentlyEditedMapping.set(null);
      },
      error: (error) => {
        const snackbar = inject(MatSnackBar);
        snackbar.open(`Error saving mapping: ${error.message}`, 'Close', {
          duration: 3000,
        });
        console.error('Error saving mapping:', error);
      },
      complete: () => {
        this.isPostingMapping.set(false);
      },
    });
  }
}
