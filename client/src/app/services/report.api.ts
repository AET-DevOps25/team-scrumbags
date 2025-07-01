import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../environment';
import { Report } from '../models/report.model';

@Injectable({
  providedIn: 'root',
})
export class ReportApi {
  private http = inject(HttpClient);

  getReportsMetadata(projectId: string): Observable<Report[]> {
    return this.http
      .get<Report[]>(`${environment.genAiUrl}/projects/${projectId}/reports`)
      .pipe(catchError(this.handleError('Error fetching reports metadata')));
  }

  generateReport(
    projectId: string,
    periodStart: Date | null,
    periodEnd: Date | null,
    userIds?: string[]
  ): Observable<Report> {
    const url = `${environment.genAiUrl}/projects/${projectId}/reports`;
    const body: Partial<{ periodStart: string; periodEnd: string; userIds: string[] }> = {};
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
      .pipe(catchError(this.handleError('Error generating report')));
  }

  getReportContent(projectId: string, reportId: string): Observable<Report> {
    return this.http
      .get<Report>(
        `${environment.genAiUrl}/projects/${projectId}/reports/${reportId}/content`
      )
      .pipe(catchError(this.handleError('Error fetching report content')));
  }

  private handleError(operation: string) {
    return (error: HttpErrorResponse): Observable<never> => {
      console.error(`${operation}:`, error);

      let errorMessage = 'An unknown error occurred';
      if (error.error instanceof ErrorEvent) {
        // Client-side error
        errorMessage = `Client error: ${error.error.message}`;
      } else {
        // Server-side error
        errorMessage = `Server error: ${error.status} ${error.statusText}`;
      }

      return throwError(() => new Error(errorMessage));
    };
  }
}
