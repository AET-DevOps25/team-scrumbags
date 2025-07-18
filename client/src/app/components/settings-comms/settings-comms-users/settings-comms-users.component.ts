import {
  Component,
  computed,
  effect,
  inject,
  OnInit,
  signal,
} from '@angular/core';
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
import { CommsUserRefreshService } from '../../../services/comms-user-refresh.service';
import { catchError, EMPTY, finalize } from 'rxjs';
import { MatGridListModule } from '@angular/material/grid-list';

@Component({
  selector: 'app-settings-comms-users',
  imports: [
    MatGridListModule,
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './settings-comms-users.component.html',
  styleUrl: './settings-comms-users.component.scss',
})
export class CommsSettingsUsers implements OnInit {
  private projectService = inject(ProjectService);
  private commsApi = inject(CommsApi);
  private commsUserRefreshService = inject(CommsUserRefreshService);
  private snackBar = inject(MatSnackBar);

  projectUsersUsernameMap = computed<Map<string, User>>(() => {
    const users = this.projectService.selectedProject()?.users ?? [];
    const map = new Map();
    users.forEach((user) => {
      map.set(user.username, user);
    });
    return map;
  });

  projectUsersIdMap = computed<Map<string, User>>(() => {
    const users = this.projectService.selectedProject()?.users ?? [];
    const map = new Map();
    users.forEach((user) => {
      map.set(user.id, user);
    });
    return map;
  });

  currentlyEditedEntry = signal<number | null>(null);
  currentUsernameInput = signal<string>('');

  commsUsers = signal<CommsUserMapping[]>([]);

  isLoadingMappings = signal(false);
  isPostingUsername = signal(false);

  constructor() {
    effect(() => {
      const projectId = this.projectService.selectedProject()?.id;
      if (projectId) {
        this.getCommsUserListFromApi(projectId);
      }
    });
  }

  ngOnInit(): void {
    this.commsUserRefreshService.onRefreshUsers.subscribe(() => {
      const projectId = this.projectService.selectedProject()?.id;
      if (projectId) {
        this.getCommsUserListFromApi(projectId);
      }
    });
  }

  getCommsUserListFromApi(projectId: string) {
    this.isLoadingMappings.set(true);
    this.commsApi.getAllCommsUsers(projectId).subscribe({
      next: (mappings) => {
        this.commsUsers.set(mappings);
      },
      complete: () => {
        this.isLoadingMappings.set(false);
      },
    });
  }

  onSubmitUsername() {
    const projectId = this.projectService.selectedProject()?.id ?? null;
    const submittedUsername = this.currentUsernameInput();
    const submittedIndex = this.currentlyEditedEntry();

    if (
      projectId === null ||
      submittedUsername === null ||
      submittedIndex === null
    ) {
      return;
    }

    const userMapping = this.commsUsers()[submittedIndex];
    const submittedUserId =
      this.projectUsersUsernameMap().get(submittedUsername)?.id ?? undefined;

    if (submittedUserId === undefined) {
      return;
    }

    userMapping.userId = submittedUserId;

    this.isPostingUsername.set(true);

    this.commsApi
      .saveUserMapping(projectId, userMapping)
      .pipe(
        catchError((error) => {
          this.snackBar.open(
            `Error saving username: ${error.message}`,
            'Close',
            {
              duration: 3000,
            }
          );
          return EMPTY;
        }),
        finalize(() => this.isPostingUsername.set(false))
      )
      .subscribe({
        next: () => {
          const tmpCommsUsers = this.commsUsers();
          tmpCommsUsers[submittedIndex].userId = submittedUserId;
          this.commsUsers.set(tmpCommsUsers);
          this.currentlyEditedEntry.set(null);
          this.currentUsernameInput.set('');
        },
      });
  }

  getUsernameForInputField(commsUser: CommsUserMapping) {
    if (commsUser.userId === null) {
      return '';
    } else {
      return this.projectUsersIdMap().get(commsUser.userId)?.username ?? '';
    }
  }
}
