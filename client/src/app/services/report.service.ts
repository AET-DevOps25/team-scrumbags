import { inject, Injectable } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { Report } from '../models/report.model';
import { ProjectState } from '../states/project.state';
import { ReportApi } from './report.api';

@Injectable({
  providedIn: 'root',
})
export class ReportService {
  private api = inject(ReportApi);
  private projectState = inject(ProjectState);

  public loadReportsMetadata(projectId: string): Observable<Report[]> {
    return this.api.getReports(projectId).pipe(
      tap((reports) => {
        for (const report of reports) {
          // trigger polling for each note that is still loading
          if (report.loading) {
            this.pollReport(projectId, report.id);
          }

          if (!report.name || report.name === '') {
            report.name = 'Report ' + report.id;
          }
        }

        this.projectState.setReports(projectId, reports);
      })
    );
  }

  public generateReport(
    projectId: string,
    periodStart: Date | null,
    periodEnd: Date | null,
    userIds?: string[]
  ): Observable<Report> {
    return this.api
      .generateReport(projectId, periodStart, periodEnd, userIds)
      .pipe(
        tap((report) => {
          console.log('Generated report:', report);
          if (!report.name || report.name === '') {
            report.name = 'Report ' + report.id;
          }
          this.projectState.updateReport(projectId, report);

          // trigger polling for until the report has loading false
          if (report.loading) {
            this.pollReport(projectId, report.id);
          }
        })
      );
  }

  private async pollReport(projectId: string, reportId: string, count = 0) {
    if (count >= 10) {
      console.warn(`Polling stopped for report ${reportId} after 10 attempts.`);
      return;
    }

    // Poll the note every 5 seconds until it is no longer loading
    await new Promise((resolve) => setTimeout(resolve, 5000));

    this.api.getReportbyId(projectId, reportId).subscribe((report) => {
      if (report.loading) {
        this.pollReport(projectId, reportId, count + 1);
        return;
      }

      this.projectState.updateReport(projectId, report);
    });
  }
}
