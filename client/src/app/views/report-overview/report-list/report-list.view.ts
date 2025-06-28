import { Component, computed, effect, inject, signal } from '@angular/core';
import { ProjectService } from '../../../services/project.service';
import { ReportService } from '../../../services/report.service';
import { finalize } from 'rxjs';
import { MatListModule } from '@angular/material/list';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

@Component({
  selector: 'app-report-list.view',
  imports: [MatListModule, MatProgressSpinnerModule],
  templateUrl: './report-list.view.html',
  styleUrl: './report-list.view.scss'
})
export class ReportListView {
    projectService = inject(ProjectService);
  reportService = inject(ReportService);

  reports = computed(() => {
    const project = this.projectService.selectedProject();
    return project?.reports ?? [];
  });

  isLoading = signal(false);

  constructor() {
    effect(() => {
      const projectId = this.projectService.selectedProjectId();
      if (projectId) {
        this.isLoading.set(true);
        this.reportService
          .loadReportsMetadata(projectId)
          .pipe(
            finalize(() => {
              this.isLoading.set(false);
            })
          )
          .subscribe();
      }
    });
  }

}
