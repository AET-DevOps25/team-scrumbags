import { Component, inject } from '@angular/core';
import { SettingsSdlcTokenComponent } from './settings-sdlc-token/settings-sdlc-token.component';
import { SettingsSdlcUsersComponent } from './settings-sdlc-users/settings-sdlc-users.component';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { SettingsSdlcInfoDialog } from './settings-sdlc-info/settings-sdlc-info.component';

@Component({
  selector: 'app-settings-sdlc',
  imports: [
    SettingsSdlcTokenComponent,
    SettingsSdlcUsersComponent,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
  ],
  templateUrl: './settings-sdlc.component.html',
  styleUrl: './settings-sdlc.component.scss',
})
export class SdlcSettings {
  private dialog = inject(MatDialog);

  openInfoDialog() {
    this.dialog.open(SettingsSdlcInfoDialog);
  }
}
