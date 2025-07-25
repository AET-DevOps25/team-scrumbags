import { Component, inject } from '@angular/core';
import { CommsApi } from '../../../services/comms.api';
import { ProjectService } from '../../../services/project.service';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatButtonModule } from '@angular/material/button';
import { CommsUserRefreshService } from '../../../services/comms-user-refresh.service';
import { catchError, EMPTY } from 'rxjs';

@Component({
  selector: 'app-settings-comms-delete',
  imports: [MatButtonModule],
  templateUrl: './settings-comms-delete.component.html',
  styleUrl: './settings-comms-delete.component.scss',
})
export class CommsSettingsDelete {
  private projectService = inject(ProjectService);
  private commsApi = inject(CommsApi);
  private commsUserRefreshService = inject(CommsUserRefreshService);
  private snackBar = inject(MatSnackBar);

  onSubmitDeleteButton() {
    const projectId = this.projectService.selectedProject()?.id;

    if (!projectId) {
      return;
    }

    this.commsApi
      .deleteAllCommsConnections(projectId)
      .pipe(
        catchError((error) => {
          this.snackBar.open(
            `Error deleting communication integrations: ${error.message}`,
            'Close',
            {
              duration: 3000,
            }
          );
          return EMPTY;
        })
      )
      .subscribe({
        complete: () => {
          this.commsUserRefreshService.onRefreshUsers.next('');
        },
      });
  }
}
