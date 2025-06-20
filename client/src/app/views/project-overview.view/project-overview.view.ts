import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatGridListModule } from '@angular/material/grid-list';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { ProjectState } from '../../states/project.state';
import { ProjectApi } from '../../services/project.api';
import { ProjectAddDialog } from '../../components/project-add.component/project-add.component';

@Component({
  selector: 'project-overview',
  imports: [
    CommonModule,
    MatCardModule,
    MatProgressSpinnerModule,
    MatGridListModule,
    MatButtonModule,
    MatIconModule
  ],
  templateUrl: './project-overview.view.html',
  styleUrl: './project-overview.view.scss',
})
export class ProjectOverviewView {
  protected state = inject(ProjectState);
  private api = inject(ProjectApi);
  private router = inject(Router);
  private dialog = inject(MatDialog);

  readonly loading = signal<boolean>(false);

  ngOnInit(): void {
    // load project list from API
    this.loading.set(true);
    this.api.getProjectList().subscribe({
      next: (projectList) => {
        this.loading.set(false);
        console.log(projectList);
        this.state.setProjectList(projectList);
      },
      error: (error) => {
        this.loading.set(false);
        console.error('Error loading project list:', error);
      },
    });
  }

  navigateToProject(projectId: number): void {
    this.router.navigate(['/projects', projectId]);
  }

  openAddProjectDialog(): void {
    this.dialog.open(ProjectAddDialog,
      {
        maxWidth: "80rem",
        minWidth: "50rem",
      }
    );
  }
}
