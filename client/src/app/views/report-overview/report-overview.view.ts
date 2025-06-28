import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ReportService } from '../../services/report.service';
import { ProjectService } from '../../services/project.service';
import { ReportListView } from './report-list/report-list.view';
import { ReportContentView } from './report-content/report-content.view';
import { UserService } from '../../services/user.service';

@Component({
  selector: 'app-report-overview',
  standalone: true,
  imports: [
    FormsModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    ReportListView,
    ReportContentView,
  ],
  templateUrl: './report-overview.view.html',
  styleUrl: './report-overview.view.scss',
})
export class ReportOverviewView {
  private userService = inject(UserService);
  private projectService = inject(ProjectService);
  private reportService = inject(ReportService);

  selectedReportId = signal<string | undefined>(undefined);

  possibleUsers = computed(
    () => this.projectService.selectedProject()?.users ?? []
  );
  showForm = signal(false);
  periodStart = signal<string | undefined>(undefined);
  periodEnd = signal<string | undefined>(undefined);
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
    this.periodStart.set(undefined);
    this.periodEnd.set(undefined);
    this.userIds.set([]);
  }

  private generateReport() {
    const projectId = this.projectService.selectedProjectId();
    if (projectId) {
      this.reportService
        .generateReport(
          projectId,
          this.periodStart(),
          this.periodEnd(),
          this.userIds()
        )
        .subscribe();
    }
  }
}
