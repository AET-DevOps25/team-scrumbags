import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatStepperModule } from '@angular/material/stepper';
import { MatIconModule } from '@angular/material/icon';
import { CdkStepper } from '@angular/cdk/stepper';

@Component({
  selector: 'app-settings-sdlc-info',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatIconModule, MatStepperModule],
  templateUrl: './settings-sdlc-info.component.html',
  styleUrl: './settings-sdlc-info.component.scss',
  providers: [{ provide: CdkStepper }],
})
export class SettingsSdlcInfoDialog {
  private dialogRef = inject(MatDialogRef<SettingsSdlcInfoDialog>);

  onCancel() {
    this.dialogRef.close();
  }
}
