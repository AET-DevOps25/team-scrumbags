import { Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { CommsApi } from '../../../services/comms.api';
import { ProjectService } from '../../../services/project.service';
import { MatSnackBar } from '@angular/material/snack-bar';
import { CommsSettingsServerPlatformPicker } from './settings-comms-platform-picker/settings-comms-platform-picker.component';
import { CommsPlatformConnection } from '../../../models/comms-platform-connection.model';

@Component({
  selector: 'app-settings-comms-server-id',
  imports: [
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    CommsSettingsServerPlatformPicker,
  ],
  templateUrl: './settings-comms-server-id.component.html',
  styleUrl: './settings-comms-server-id.component.scss'
})
export class CommsSettingsServerId {
  private projectService = inject(ProjectService);
  private commsApi = inject(CommsApi);
  private platformPicker = inject(CommsSettingsServerPlatformPicker);

  showServerId = signal(false);
  serverIdInput = signal('');

  isLoading = signal(false);
  confirmation = signal(false);

  constructor() {
    effect(() => {
      this.isLoading.set(true);
      this.serverIdInput.set('');
      const projectId = this.projectService.selectedProject()?.id;
      if (projectId) {
        this.isLoading.set(false);
      }
    });
  }

  onSubmit() {
    const projectId = this.projectService.selectedProject()?.id;
    if (!projectId) {
      console.error('No project selected');
      return;
    }

    this.isLoading.set(true);

    this.commsApi.addCommsConnection(projectId, this.serverIdInput(), this.platformPicker.getSelectedPlatform()).subscribe({
      next: (connection) => {
        if (connection) {
          this.serverIdInput.set('');
          this.confirmation.set(true);
        }
      },
      error: (error) => {
        const snackbar = inject(MatSnackBar);
        snackbar.open(`Error saving token: ${error.message}`, 'Close', {
          duration: 3000,
        });
        console.error('Error saving token:', error);
      },
      complete: () => {
        this.isLoading.set(false);
      },
    });
  }
}
