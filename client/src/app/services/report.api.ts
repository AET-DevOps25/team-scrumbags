import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../environment';
import { Report } from '../models/report.model';
import { handleError } from './api-utils';

@Injectable({
  providedIn: 'root',
})
export class ReportApi {
  private http = inject(HttpClient);

  getReportsMetadata(projectId: string): Observable<Report[]> {
    return this.http
      .get<Report[]>(`${environment.genAiUrl}/projects/${projectId}/reports`)
      .pipe(catchError(handleError('Error fetching reports metadata')));
  }

  generateReport(
    projectId: string,
    periodStart: Date | null,
    periodEnd: Date | null,
    userIds?: string[]
  ): Observable<Report> {
    const url = `${environment.genAiUrl}/projects/${projectId}/reports`;
    const body: Partial<{
      periodStart: string;
      periodEnd: string;
      userIds: string[];
    }> = {};
    if (periodStart) {
      body.periodStart = periodStart.toISOString();
    }
    if (periodEnd) {
      body.periodEnd = periodEnd.toISOString();
    }
    if (userIds && userIds.length > 0) {
      body.userIds = userIds;
    }

    return this.http
      .post<Report>(url, body)
      .pipe(catchError(handleError('Error generating report')));
  }

  getReportContent(projectId: string, reportId: string): Observable<Report> {
    return this.http
      .get<Report>(
        `${environment.genAiUrl}/projects/${projectId}/reports/${reportId}/content`
      )
      .pipe(catchError(handleError('Error fetching report content')));
  }
}
