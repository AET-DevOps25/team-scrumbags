import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ReportService } from '../../services/report.service';
import { ProjectService } from '../../services/project.service';
import { ReportListView } from './report-list/report-list.view';
import { ReportContentView } from './report-content/report-content.view';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';

@Component({
  selector: 'app-report-overview',
  standalone: true,
  imports: [
    FormsModule,
    MatInputModule,
    MatFormFieldModule,
    ReactiveFormsModule,
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

  selectedReportId = signal<string | undefined>(undefined);

  possibleUsers = computed(
    () => this.projectService.selectedProject()?.users ?? []
  );
  showForm = signal(false);

  // undefined for invalid date and null for empty date
  periodStart = signal<Date | null>(null);
  periodEnd = signal<Date | null>(null);
  now = computed(() => {
    this.periodStart();
    this.periodEnd();
    return new Date();
  });
  userIds = signal<string[]>([]);

  displayForm() {
    this.showForm.set(true);
    const projectId = this.projectService.selectedProjectId();
    if (!projectId) {
      return;
    }
    this.projectService.loadUsersOfProject(projectId).subscribe();
  }

  onSubmit() {
    this.generateReport();
    this.showForm.set(false);
  }

  onCancel() {
    this.showForm.set(false);
    this.periodStart.set(null);
    this.periodEnd.set(null);
    this.userIds.set([]);
  }

  private generateReport() {
    const projectId = this.projectService.selectedProjectId();
    const periodStart = this.periodStart();
    const periodEnd = this.periodEnd();

    if (projectId) {
      this.reportService
        .generateReport(projectId, periodStart, periodEnd, this.userIds())
        .subscribe();
    }
  }
}
