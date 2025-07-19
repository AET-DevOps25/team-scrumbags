import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { CommsApi } from '../../../services/comms.api';
import { ProjectService } from '../../../services/project.service';
import { MatSnackBar } from '@angular/material/snack-bar';
import { SupportedCommsPlatforms } from '../../../enums/supported-comms-platforms.enum';
import { MatSelectModule } from '@angular/material/select';
import { CommsUserRefreshService } from '../../../services/comms-user-refresh.service';
import { catchError, finalize } from 'rxjs/operators';
import { EMPTY } from 'rxjs';

@Component({
  selector: 'app-settings-comms-server-id',
  imports: [
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSelectModule,
  ],
  templateUrl: './settings-comms-server-id.component.html',
  styleUrl: './settings-comms-server-id.component.scss',
})
export class CommsSettingsServerId {
  private projectService = inject(ProjectService);
  private commsApi = inject(CommsApi);
  private commsUserRefreshService = inject(CommsUserRefreshService);
  private snackBar = inject(MatSnackBar);

  showServerId = signal(false);
  serverIdInput = signal('');

  isLoading = signal(false);
  confirmation = signal(false);

  platforms = Object.values(SupportedCommsPlatforms);

  selectedPlatform = signal(SupportedCommsPlatforms.DISCORD);

  onSubmit() {
    const projectId = this.projectService.selectedProject()?.id;
    if (!projectId) {
      console.error('No project selected');
      return;
    }

    this.isLoading.set(true);

    this.commsApi
      .addCommsConnection(
        projectId,
        this.serverIdInput(),
        this.selectedPlatform()
      )
      .pipe(
        catchError((error) => {
          this.snackBar.open(
            `Error saving server ID: ${error.message}`,
            'Close',
            {
              duration: 3000,
            }
          );
          return EMPTY;
        }),
        finalize(() => {
          this.isLoading.set(false);
        })
      )
      .subscribe({
        next: (connection) => {
          if (connection) {
            this.serverIdInput.set('');
            this.confirmation.set(true);
          }
        },
        complete: () => {
          this.commsUserRefreshService.onRefreshUsers.next('');
        },
      });
  }
}
