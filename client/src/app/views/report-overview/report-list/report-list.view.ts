import {
  Component,
  computed,
  effect,
  inject,
  output,
  signal,
} from '@angular/core';
import { ProjectService } from '../../../services/project.service';
import { ReportService } from '../../../services/report.service';
import { finalize } from 'rxjs';
import { MatListModule } from '@angular/material/list';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

@Component({
  selector: 'app-report-list',
  imports: [MatListModule, MatProgressSpinnerModule],
  templateUrl: './report-list.view.html',
  styleUrl: './report-list.view.scss',
})
export class ReportListView {
  projectService = inject(ProjectService);
  reportService = inject(ReportService);

  selectedReportId = output<string | undefined>();

  reports = computed(() => {
    const project = this.projectService.selectedProject();
    if (!project || !project.reports) {
      return [];
    }

    return Array.from(project.reports.values());
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

  onReportSelected(reportId: string) {
    this.selectedReportId.emit(reportId);
  }
}
