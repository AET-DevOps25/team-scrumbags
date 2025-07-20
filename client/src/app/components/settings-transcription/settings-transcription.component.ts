import { Component } from '@angular/core';
import { SettingsTranscriptionUsersComponent } from './settings-transcription-users/settings-transcription-users.component';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

@Component({
  selector: 'app-settings-transcription',
  standalone: true,
  imports: [
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    SettingsTranscriptionUsersComponent,
  ],
  templateUrl: './settings-transcription.component.html',
  styleUrl: './settings-transcription.component.scss',
})
export class TranscriptionSettings {}
