import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { ProjectService } from '../../../services/project.service';
import { ReportService } from '../../../services/report.service';
import { ActivatedRoute } from '@angular/router';
import { finalize } from 'rxjs';

@Component({
  selector: 'app-report-content.view',
  imports: [],
  templateUrl: './report-content.view.html',
  styleUrl: './report-content.view.scss',
})
export class ReportContentView implements OnInit {
  private projectService = inject(ProjectService);
  private reportService = inject(ReportService);
  private route = inject(ActivatedRoute);

  report = computed(() => {
    const project = this.projectService.selectedProject();
    const reportId = this.reportId();
    if (!project || !reportId) {
      return null;
    }

    return project?.reports.find((report) => report.id === reportId) ?? null;
  });

  reportId = signal<string | null>(null);
  isLoading = signal(false);

  ngOnInit(): void {
    // on url path change, update reportId
    this.route.paramMap.subscribe((params) => {
      const reportId = params.get('reportId');
      if (reportId) {
        this.reportId.set(reportId);
        const projectId = this.projectService.selectedProjectId();
        if (projectId) {
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
    });
  }
}
