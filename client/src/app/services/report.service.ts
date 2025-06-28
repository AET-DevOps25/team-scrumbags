import { inject, Injectable } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { MeetingNote } from '../models/meeting-note.model';
import { ProjectState } from '../states/project.state';
import { ReportApi } from './report.api';

@Injectable({
  providedIn: 'root',
})
export class ReportService {
  private api = inject(ReportApi);
  private projectState = inject(ProjectState);

  public loadReportsMetadata(projectId: string): Observable<MeetingNote[]> {
    return this.api.getReportsMetadata(projectId).pipe(
      tap((reportMetadata) => {
        this.projectState.setReports(projectId, reportMetadata);
      })
    );
  }

  public generateReport(
    projectId: string,
    periodStart?: string,
    periodEnd?: string,
    userIds?: string[]
  ): Observable<MeetingNote> {
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
  ): Observable<MeetingNote> {
    return this.api.getReportContent(projectId, reportId).pipe(
      tap((report) => {
        this.projectState.updateReport(projectId, report);
      })
    );
  }
}
