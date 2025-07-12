import {
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { ProjectService } from '../../../services/project.service';
import { ReportService } from '../../../services/report.service';
import { finalize } from 'rxjs';
import { DatePipe } from '@angular/common';

@Component({
  selector: 'app-report-content',
  imports: [
    DatePipe
  ],
  templateUrl: './report-content.view.html',
  styleUrl: './report-content.view.scss',
})
export class ReportContentView {
  private projectService = inject(ProjectService);
  private reportService = inject(ReportService);

  reportId = input<string>();

  report = computed(() => {
    const project = this.projectService.selectedProject();
    const reportId = this.reportId();
    if (!project || !reportId) {
      return null;
    }

    return project?.reports.find((report) => report.id === reportId) ?? null;
  });

  isLoading = signal(false);

  constructor() {
    effect(() => {
      const projectId = this.projectService.selectedProjectId();
      const reportId = this.reportId();
      if (projectId && reportId) {
        this.loadContent(projectId, reportId);
      }
    });
  }

  loadContent(projectId: string, reportId: string) {
    this.isLoading.set(true);
    this.reportService
      .loadReportContent(projectId, reportId)
      .pipe(
        finalize(() => {
          this.isLoading.set(false);
        })
      )
      .subscribe();
  }
}
