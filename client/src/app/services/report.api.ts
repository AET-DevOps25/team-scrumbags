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
      .get<Report[]>(`${environment.genAiUrl}/projects/${projectId}/summary`)
      .pipe(catchError(handleError('Error fetching reports metadata')));
  }

  generateReport(
    projectId: string,
    periodStart: Date | null,
    periodEnd: Date | null,
    userIds?: string[]
  ): Observable<Report> {
    const url = `${environment.genAiUrl}/projects/${projectId}/summary`;
    let params = new URLSearchParams();
    if (periodStart) {
      params.append('startTime', periodStart.toISOString());
    }
    if (periodEnd) {
      params.append('endTime', periodEnd.toISOString());
    }
    if (userIds && userIds.length > 0) {
      userIds.forEach((id) => params.append('userIds', id));
    }

    return this.http
      .get<Report>(`${url}?${params.toString()}`)
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
