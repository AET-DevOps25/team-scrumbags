import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';

@Component({
  selector: 'app-settings-comms-info.component',
  imports: [MatDialogModule, MatButtonModule, MatIconModule, MatListModule],
  templateUrl: './settings-comms-info.component.html',
  styleUrl: './settings-comms-info.component.scss',
})
export class SettingsCommsInfoDialog {
  private dialogRef = inject(MatDialogRef<SettingsCommsInfoDialog>);

  onCancel() {
    this.dialogRef.close();
  }
}
