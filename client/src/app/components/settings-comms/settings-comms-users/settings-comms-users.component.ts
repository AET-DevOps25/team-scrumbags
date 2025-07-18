import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { CommsApi } from '../../../services/comms.api';
import { ProjectService } from '../../../services/project.service';
import { CommsUserMapping } from '../../../models/comms-users.model';
import { User } from '../../../models/user.model';
import { MatSnackBar } from '@angular/material/snack-bar';
import { SupportedCommsPlatforms } from '../../../enums/supported-comms-platforms.enum';

@Component({
  selector: 'app-settings-comms-users',
  imports: [
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './settings-comms-users.component.html',
  styleUrl: './settings-comms-users.component.scss'
})
export class CommsSettingsUsers {
  private projectService = inject(ProjectService);
  private commsApi = inject(CommsApi);

  private projectsUser = computed<User[]>(() => {
    return this.projectService.selectedProject()?.users ?? [];
  });

  currentlyEditedMapping = signal<CommsUserMapping | null>(null);
  private userMappings = signal<Map<string, CommsUserMapping>>(new Map());
  userMappingMap = computed<{ user: User; mapping: CommsUserMapping }[]>(() => {
    const users = this.projectsUser();
    const mappings = this.userMappings();
    const projectId = this.projectService.selectedProjectId();
    if (!projectId) {
      return [];
    }

    return users.map((user) => {
      const mapping = mappings.get(user.id) ?? {
        userId: user.id,
        platformUserId: '',
        platform: SupportedCommsPlatforms.DISCORD,
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
        this.commsApi.getAllCommsUsers(projectId).subscribe({
          next: (mappings) => {
            const map = new Map<string, CommsUserMapping>();
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
    this.commsApi.saveUserMapping(projectId, submittedMapping).subscribe({
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
