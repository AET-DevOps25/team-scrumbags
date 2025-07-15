import { Component, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Report } from '../../../models/report.model';
import {MarkdownComponent} from 'ngx-markdown';

@Component({
  selector: 'app-report-content',
  imports: [DatePipe, MarkdownComponent],
  templateUrl: './report-content.view.html',
  styleUrl: './report-content.view.scss',
})
export class ReportContentView {
  report = input.required<Report>();
}
