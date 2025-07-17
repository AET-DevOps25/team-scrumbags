import { Component, effect, signal } from '@angular/core';
import { MatSelect } from '@angular/material/select';
import { SupportedCommsPlatforms } from '../../../../enums/supported-comms-platforms.enum';

@Component({
  selector: 'app-settings-comms-platform-picker',
  imports: [
    MatSelect,
  ],
  templateUrl: './settings-comms-platform-picker.component.html',
  styleUrl: './settings-comms-platform-picker.component.scss'
})
export class CommsSettingsServerPlatformPicker {
  platforms = Object.values(SupportedCommsPlatforms);

  selectedPlatform = signal(SupportedCommsPlatforms.DISCORD);
  
  constructor() {
    effect(() => {
      this.selectedPlatform.set(SupportedCommsPlatforms.DISCORD); // Default selection
    });
  }

  onPlatformChange(platform: SupportedCommsPlatforms) {
    this.selectedPlatform.set(platform);
    console.log('Selected platform:', this.selectedPlatform);
  }

  getSelectedPlatform(): SupportedCommsPlatforms {
    return this.selectedPlatform();
  }
}
