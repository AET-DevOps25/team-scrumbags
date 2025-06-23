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
import { UserApi } from '../../services/user.api';
import { ProjectApi } from '../../services/project.api';
import { MatSnackBar } from '@angular/material/snack-bar';
import { UserService } from '../../services/user.service';
import { UserState } from '../../states/user.state';
import { ProjectState } from '../../states/project.state';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-project-people',
  imports: [
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatAutocompleteModule,
    FormsModule,
  ],
  templateUrl: './project-people.component.html',
  styleUrl: './project-people.component.scss',
})
export class ProjectPeopleComponent implements OnInit {
  projectService = inject(ProjectService);
  projectState = inject(ProjectState);
  userService = inject(UserService);
  userState = inject(UserState);
  projectApi = inject(ProjectApi);

  projectsUser = computed<User[]>(() => {
    const project = this.projectService.selectedProject();
    console.log('selectedProject', project);
    console.log(
      'project users property exists:',
      project && 'users' in project
    );
    console.log('users value:', project?.users);
    console.log('users type:', typeof project?.users);
    return project?.users ?? [];
  });

  usersToAdd = computed<User[]>(() => {
    const allUsers = Array.from(this.userState.allUser().values());
    return allUsers.filter(
      (user) => !this.projectsUser().some((u) => u.id === user.id)
    );
  });

  loadingProjectsUser = signal(false);
  userSearch = '';

  constructor() {
    effect(() => {
      const projectId = this.projectService.selectedProjectId();
      if (projectId) {
        this.loadUsersOfProject(projectId);
      }
    });
  }

  ngOnInit(): void {
    this.loadAllUsers();
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
      .subscribe();
  }

  removeUserFromProject(userId: string) {
    this.projectService
      .removeUserFromProject(this.projectService.selectedProjectId()!, [userId])
      .subscribe();
  }
}
