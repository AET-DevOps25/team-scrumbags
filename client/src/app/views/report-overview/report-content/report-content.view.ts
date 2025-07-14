import { Component, effect, inject, input, signal } from '@angular/core';
import { ProjectService } from '../../../services/project.service';
import { ReportService } from '../../../services/report.service';
import { finalize } from 'rxjs';
import { DatePipe } from '@angular/common';
import { Report } from '../../../models/report.model';

@Component({
  selector: 'app-report-content',
  imports: [DatePipe],
  templateUrl: './report-content.view.html',
  styleUrl: './report-content.view.scss',
})
export class ReportContentView {
  report = input.required<Report>();
}
