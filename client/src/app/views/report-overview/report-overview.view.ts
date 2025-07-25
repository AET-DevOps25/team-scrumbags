import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatMenuModule } from '@angular/material/menu';
import { ReportService } from '../../services/report.service';
import { ProjectService } from '../../services/project.service';
import { ReportListView } from './report-list/report-list.view';
import { ReportContentView } from './report-content/report-content.view';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule, MatOptionModule } from '@angular/material/core';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, EMPTY } from 'rxjs';

@Component({
  selector: 'app-report-overview',
  standalone: true,
  imports: [
    MatToolbarModule,
    FormsModule,
    MatInputModule,
    MatFormFieldModule,
    ReactiveFormsModule,
    MatMenuModule,
    MatOptionModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    ReportListView,
    ReportContentView,
  ],
  templateUrl: './report-overview.view.html',
  styleUrl: './report-overview.view.scss',
})
export class ReportOverviewView {
  private projectService = inject(ProjectService);
  private reportService = inject(ReportService);
  private snackBar = inject(MatSnackBar);

  selectedReportId = signal<string | undefined>(undefined);
  selectedReport = computed(() => {
    const project = this.projectService.selectedProject();
    const reportId = this.selectedReportId();
    if (!project || !reportId) {
      return undefined;
    }

    return project.reports?.get(reportId) ?? undefined;
  });

  possibleUsers = computed(() => {
    const users = this.projectService.selectedProject()?.users ?? [];
    return users;
  });

  // undefined for invalid date and null for empty date
  periodStart = signal<Date | null>(null);
  periodEnd = signal<Date | null>(null);
  now = computed(() => {
    this.periodStart();
    this.periodEnd();
    return new Date();
  });
  userIds = signal<string[]>([]);

  openMenu() {
    const projectId = this.projectService.selectedProjectId();
    if (!projectId) {
      return;
    }
    this.projectService
      .loadUsersOfProject(projectId)
      .pipe(
        catchError((error) => {
          this.snackBar.open(`Error loading users: ${error.message}`, 'Close', {
            duration: 3000,
          });
          return EMPTY;
        })
      )
      .subscribe();
  }

  onSubmit() {
    this.generateReport(this.periodStart(), this.periodEnd(), this.userIds());
  }

  onCancel() {
    this.periodStart.set(null);
    this.periodEnd.set(null);
    this.userIds.set([]);
  }

  private generateReport(
    periodStart: Date | null,
    periodEnd: Date | null,
    userIds?: string[]
  ) {
    const projectId = this.projectService.selectedProjectId();

    if (projectId) {
      this.reportService
        .generateReport(projectId, periodStart, periodEnd, userIds)
        .pipe(
          catchError((error) => {
            this.snackBar.open(
              `Error generating report: ${error.message}`,
              'Close',
              { duration: 3000 }
            );
            return EMPTY;
          })
        )
        .subscribe();
    }
  }
}
