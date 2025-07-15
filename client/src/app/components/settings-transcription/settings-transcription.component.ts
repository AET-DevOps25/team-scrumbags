import { Component } from '@angular/core';
import { SettingsTranscriptionUsersComponent} from './settings-transcription-users/settings-transcription-users.component';

@Component({
  selector: 'app-settings-transcription',
  standalone: true,
  imports: [SettingsTranscriptionUsersComponent],
  templateUrl: './settings-transcription.component.html',
  styleUrl: './settings-transcription.component.scss',
})
export class TranscriptionSettings {}
