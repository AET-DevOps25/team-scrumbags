import { Component, input } from '@angular/core';
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
