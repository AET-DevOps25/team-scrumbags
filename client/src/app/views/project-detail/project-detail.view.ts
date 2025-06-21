import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ProjectState } from '../../states/project.state';
import { ProjectApi } from '../../services/project.api';
import { Project } from '../../models/project.model';
import { MatButtonModule } from '@angular/material/button';
import { CommonModule } from '@angular/common';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';

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
  private route = inject(ActivatedRoute);
  readonly loading = signal<boolean>(false);

  projectId = signal<string | null>(null);
  project = computed<Project | null>(() => {
    const projectId = this.projectId();
    return projectId ? this.state.findProjectById(projectId) : null;
  });

  protected state = inject(ProjectState);
  private api = inject(ProjectApi);

  ngOnInit(): void {
    this.route.params.subscribe((params) => {
      const projectId = params['id'];
      this.projectId.set(projectId);

      if (projectId) {
        this.loadProject(projectId);
      } else {
        console.error('No project ID provided in route parameters.');
      }
    });
  }

  private loadProject(projectId: string) {
    this.loading.set(true);
    this.api.getProjectById(projectId).subscribe({
      next: (project) => {
        this.state.setProjectById(projectId, project);
        console.log(project);
        this.loading.set(false);
      },
      error: (error) => {
        this.loading.set(false);
        console.error('Error loading project list:', error);
      },
    });
  }

  protected openSettingsDialog(): void {}
}
