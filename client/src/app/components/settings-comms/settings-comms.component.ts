import { Component, inject } from '@angular/core';
import { CommsSettingsServerId } from './settings-comms-server-id/settings-comms-server-id.component';
import { CommsSettingsUsers } from './settings-comms-users/settings-comms-users.component';
import { CommsSettingsDelete } from './settings-comms-delete/settings-comms-delete.component';
import { MatDialog } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { SettingsCommsInfoDialog } from './settings-comms-info.component/settings-comms-info.component';

@Component({
  selector: 'app-settings-comms',
  imports: [
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    CommsSettingsServerId,
    CommsSettingsUsers,
    CommsSettingsDelete,
  ],
  templateUrl: './settings-comms.component.html',
  styleUrl: './settings-comms.component.scss',
})
export class CommsSettings {
  private dialog = inject(MatDialog);

  openInfoDialog() {
    this.dialog.open(SettingsCommsInfoDialog, {
      panelClass: 'w-xl',
    });
  }
}
