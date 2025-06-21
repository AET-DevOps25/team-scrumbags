import { Component, inject, signal } from '@angular/core';
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
    RouterOutlet
  ],
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.scss']
})
export class SidebarComponent {
  protected state = inject(ProjectState);
  private router = inject(Router);
  private dialog = inject(MatDialog);

  readonly sidebarOpened = signal<boolean>(true);

  toggleSidebar(): void {
    this.sidebarOpened.update(opened => !opened);
  }

  navigateToProjectOverview(): void {
    this.router.navigate(['/projects']);
  }

  navigateToProject(projectId: number): void {
    this.router.navigate(['/projects', projectId]);
  }

  openAddProjectDialog(): void {
    const dialogRef = this.dialog.open(ProjectAddDialog, {
      width: '500px',
      disableClose: false
    });

    dialogRef.afterClosed().subscribe(newProject => {
      if (newProject) {
        this.state.setProjectById(newProject.id, newProject);
      }
    });
  }
}
