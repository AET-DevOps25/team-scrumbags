import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { Project } from '../../models/project.model';
import { ProjectState } from '../../states/project.state';
import { ProjectService } from '../../services/project.service';
import { ActivatedRoute } from '@angular/router';
import { finalize } from 'rxjs';

@Component({
  selector: 'project-settings',
  imports: [],
  templateUrl: './project-settings.view.html',
  styleUrl: './project-settings.view.scss',
})
export class ProjectSettingsView implements OnInit {
  protected state = inject(ProjectState);
  private service = inject(ProjectService);
  private route = inject(ActivatedRoute);

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
}
