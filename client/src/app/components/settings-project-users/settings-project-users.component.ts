import {
  Component,
  computed,
  effect,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { User } from '../../models/user.model';
import { ProjectService } from '../../services/project.service';
import { UserService } from '../../services/user.service';
import { UserState } from '../../states/user.state';
import { ProjectState } from '../../states/project.state';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { catchError, EMPTY } from 'rxjs';
import { MatSnackBar } from '@angular/material/snack-bar';

@Component({
  selector: 'app-settings-project-users',
  imports: [
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatAutocompleteModule,
    ReactiveFormsModule,
    FormsModule,
  ],
  templateUrl: './settings-project-users.component.html',
  styleUrl: './settings-project-users.component.scss',
})
export class ProjectUserSettings implements OnInit {
  projectService = inject(ProjectService);
  projectState = inject(ProjectState);
  userService = inject(UserService);
  userState = inject(UserState);
  private snackBar = inject(MatSnackBar);

  loadingProjectsUser = signal(false);
  signedInUser = signal<User | undefined>(undefined);
  projectsUser = computed<User[]>(() => {
    return this.projectService.selectedProject()?.users ?? [];
  });

  userSearch = signal('');
  private usersToAdd = computed<User[]>(() => {
    const allUsers = Array.from(this.userState.allUser().values());
    return allUsers.filter(
      (user) => !this.projectsUser().some((u) => u.id === user.id)
    );
  });
  filteredUsersToAdd = computed(() =>
    this.filterOptions(this.userSearch(), this.usersToAdd())
  );

  constructor() {
    effect(() => {
      const projectId = this.projectService.selectedProjectId();
      if (projectId) {
        this.loadUsersOfProject(projectId);
      }
    });
  }

  ngOnInit(): void {
    this.signedInUser.set(this.userService.getSignedInUser() ?? undefined);
    this.loadAllUsers();
  }

  private filterOptions(searchInput: string, users: User[]): User[] {
    searchInput = searchInput.toLowerCase();
    return users.filter(
      (u) => u.username?.includes(searchInput) || u.email?.includes(searchInput)
    );
  }

  displayFn(user: User): string {
    return user && user.username ? user.username : '';
  }

  private loadUsersOfProject(projectId: string) {
    this.loadingProjectsUser.set(true);
    this.projectService.loadUsersOfProject(projectId).subscribe({
      next: () => {
        this.loadingProjectsUser.set(false);
      },
    });
  }

  loadAllUsers() {
    this.userService.loadAllUsers().subscribe();
  }

  addUserToProject(user: User) {
    this.projectService
      .assignUserToProject(this.projectService.selectedProjectId()!, [user])
      .pipe(
        catchError((error) => {
          this.snackBar.open(
            `Error adding user to project: ${error.message}`,
            'Close',
            { duration: 3000 }
          );
          return EMPTY;
        })
      )
      .subscribe({
        next: () => this.userSearch.set(''),
      });
  }

  removeUserFromProject(userId: string) {
    this.projectService
      .removeUserFromProject(this.projectService.selectedProjectId()!, [userId])
      .pipe(
        catchError((error) => {
          this.snackBar.open(
            `Error removing user from project: ${error.message}`,
            'Close',
            { duration: 3000 }
          );
          return EMPTY;
        })
      )
      .subscribe();
  }

  onUserSelected(user: User) {
    this.addUserToProject(user);
    this.userSearch.set('');
  }
}
