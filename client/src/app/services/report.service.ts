import { inject, Injectable } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { ProjectState } from '../states/project.state';
import { Report } from '../models/report.model';
import { ReportApi } from './report.api';

@Injectable({
  providedIn: 'root',
})
export class ReportService {
  private api = inject(ReportApi);
  private projectState = inject(ProjectState);

  public loadReportsMetadata(projectId: string): Observable<Report[]> {
    return this.api.getReportsMetadata(projectId).pipe(
      tap((reportMetadata) => {
        this.projectState.setReports(projectId, reportMetadata);
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
          this.projectState.updateReport(projectId, report);
        })
      );
  }

  public loadReportContent(
    projectId: string,
    reportId: string
  ): Observable<Report> {
    return this.api.getReportContent(projectId, reportId).pipe(
      tap((report) => {
        this.projectState.updateReport(projectId, report);
      })
    );
  }
}
