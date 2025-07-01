import { Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { ProjectState } from '../../states/project.state';
import { Project } from '../../models/project.model';
import { MatButtonModule } from '@angular/material/button';
import { CommonModule } from '@angular/common';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { ProjectService } from '../../services/project.service';

@Component({
  selector: 'app-project-detail',
  imports: [
    CommonModule,
    MatProgressSpinnerModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './project-detail.view.html',
  styleUrl: './project-detail.view.scss',
})
export class ProjectDetailView {
  // extract project ID from route params
  protected state = inject(ProjectState);
  private service = inject(ProjectService);
  private router = inject(Router);

  project = computed<Project | undefined>(() => this.service.selectedProject());

  navigateToSettings(): void {
    this.router.navigate([
      '/projects',
      this.service.selectedProjectId(),
      'settings',
    ]);
  }
}
