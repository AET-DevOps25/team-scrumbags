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

  getReports(projectId: string): Observable<Report[]> {
    return this.http
      .get<Report[]>(`${environment.genAiUrl}/project/${projectId}/summary`)
      .pipe(catchError(handleError('Error fetching reports metadata')));
  }

  getReportbyId(projectId: string, reportId: string): Observable<Report> {
    return this.http
      .get<Report>(
        `${environment.genAiUrl}/project/${projectId}/summary/${reportId}`
      )
      .pipe(
        catchError(
          handleError(`Error fetching report metadata for id ${reportId}`)
        )
      );
  }

  generateReport(
    projectId: string,
    periodStart: Date | null,
    periodEnd: Date | null,
    userIds?: string[]
  ): Observable<Report> {
    const url = `${environment.genAiUrl}/project/${projectId}/summary`;
    const params = new URLSearchParams();
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
      .post<Report>(`${url}?${params.toString()}`, {})
      .pipe(catchError(handleError('Error generating report')));
  }
}
