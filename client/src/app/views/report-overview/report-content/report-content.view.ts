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
import { Report } from '../../../models/report.model';

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

  report = input.required<Report>();

  isLoading = signal(false);
  private lastReportId: string | undefined = undefined

  constructor() {
    effect(() => {
      const projectId = this.projectService.selectedProjectId();
      const reportId = this.report().id;
      if (projectId && reportId && reportId !== this.lastReportId) {
        this.lastReportId = reportId;
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
