import { Component, computed, inject, OnInit, signal } from '@angular/core';
import {
  Router,
  RouterOutlet,
} from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatDialog } from '@angular/material/dialog';
import { ProjectState } from '../../states/project.state';
import { ProjectAddDialog } from '../../components/project-add/project-add.component';
import { ProjectService } from '../../services/project.service';

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
  styleUrl: './sidebar.component.scss',
})
export class SidebarComponent implements OnInit{
  protected state = inject(ProjectState);
  protected service = inject(ProjectService);
  private router = inject(Router);
  private dialog = inject(MatDialog);

  readonly sidebarOpened = signal<boolean>(true);
  readonly loading = computed(() => this.service.isLoadingProjectList());

  ngOnInit(): void {
    // load project list from API
    this.service.loadProjectList().subscribe();
  }

  toggleSidebar(): void {
    this.sidebarOpened.update((opened) => !opened);
  }

  navigateToProjectOverview(): void {
    this.router.navigate(['/projects']);
  }

  navigateToProject(projectId: string): void {
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

  navigateToSettings(): void {
    this.router.navigate([
      '/projects',
      this.service.selectedProjectId(),
      'settings',
    ]);
  }
}
