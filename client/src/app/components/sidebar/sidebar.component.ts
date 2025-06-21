import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatDialog } from '@angular/material/dialog';
import { ProjectState } from '../../states/project.state';
import { ProjectAddDialog } from '../project-add/project-add.component';
import { ProjectApi } from '../../services/project.api';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatListModule,
    MatIconModule,
    MatSidenavModule,
    MatToolbarModule,
    RouterOutlet,
  ],
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.scss'],
})
export class SidebarComponent {
  protected state = inject(ProjectState);
  private api = inject(ProjectApi);
  private router = inject(Router);
  private dialog = inject(MatDialog);

  readonly sidebarOpened = signal<boolean>(true);
  readonly loading = computed(() => this.api.isLoadingProjectList());

  ngOnInit(): void {
    // load project list from API
    this.api.getProjectList().subscribe({
      next: (projectList) => {
        console.log(projectList);
        this.state.setProjectList(projectList);
      },
      error: (error) => {
        console.error('Error loading project list:', error);
      },
    });
  }

  toggleSidebar(): void {
    this.sidebarOpened.update((opened) => !opened);
  }

  navigateToProjectOverview(): void {
    this.router.navigate(['/projects']);
  }

  navigateToProject(projectId: string): void {
    this.toggleSidebar();
    this.router.navigate(['/projects', projectId]);
  }

  openAddProjectDialog(): void {
    const dialogRef = this.dialog.open(ProjectAddDialog, {
      width: '500px',
      disableClose: false,
    });

    dialogRef.afterClosed().subscribe((newProject) => {
      if (newProject) {
        this.state.setProjectById(newProject.id, newProject);
      }
    });
  }
}
