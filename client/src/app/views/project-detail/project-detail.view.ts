import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ProjectState } from '../../states/project.state';
import { Project } from '../../models/project.model';
import { MatButtonModule } from '@angular/material/button';
import { CommonModule } from '@angular/common';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { ProjectService } from '../../services/project.service';
import { finalize } from 'rxjs';

@Component({
  selector: 'project-detail',
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
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  projectId = signal<string | null>(null);
  project = computed<Project | null>(() => {
    const projectId = this.projectId();
    return projectId ? this.state.findProjectById(projectId) : null;
  });

  ngOnInit(): void {
    this.route.params.subscribe((params) => {
      const projectId = params['id'];
      this.projectId.set(projectId);
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
