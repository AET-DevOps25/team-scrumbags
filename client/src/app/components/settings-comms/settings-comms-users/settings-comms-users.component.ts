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

  projectUsers = computed<Map<string, User>>(() => {
    const users = this.projectService.selectedProject()?.users ?? [];
    const map = new Map();
    users.forEach(user => {
      map.set(user.id, user);
    });
    return map;
  });

  currentlyEditedUser = signal<User | null>(null);
  platformUsers = signal<Map<string, CommsUserMapping>>(new Map());

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
            this.platformUsers.set(map);
          },
          complete: () => {
            this.isLoadingMappings.set(false);
          },
        });
      }
    });
  }

  onCurrentlyEditedUserChange(userId: string) {
    const prevMapping = this.currentlyEditedUser();
    if (!prevMapping) {
      return;
    }
    const user = this.projectUsers()?.get(userId) ?? null;
    this.currentlyEditedUser.set(user);
  }

  onSubmitMapping() {
    const projectId = this.projectService.selectedProject()?.id;
    const submittedMapping = this.currentlyEditedUser();
    if (!projectId || !submittedMapping) {
      return;
    }
    const submittedCommsUser = this.platformUsers().get(submittedMapping.id);
    if (!submittedCommsUser) {
      return;
    }
    this.isPostingMapping.set(true);
    this.commsApi.saveUserMapping(projectId, submittedCommsUser).subscribe({
      next: (mapping) => {
        const currentMappings = this.platformUsers();

        const newMappings = new Map(
          currentMappings.set(mapping.userId, mapping)
        );
        this.platformUsers.set(newMappings);
        this.currentlyEditedUser.set(null);
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
