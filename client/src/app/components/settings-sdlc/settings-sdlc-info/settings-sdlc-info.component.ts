import { Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatStepperModule } from '@angular/material/stepper';
import { MatIconModule } from '@angular/material/icon';
import { CdkStepper } from '@angular/cdk/stepper';
import {ClipboardModule} from '@angular/cdk/clipboard';
import { environment } from '../../../environment';
import { ProjectService } from '../../../services/project.service';

@Component({
  selector: 'app-settings-sdlc-info',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatIconModule, MatStepperModule, ClipboardModule],
  templateUrl: './settings-sdlc-info.component.html',
  styleUrl: './settings-sdlc-info.component.scss',
  providers: [{ provide: CdkStepper }],
})
export class SettingsSdlcInfoDialog {
  private projectService = inject(ProjectService);
  private dialogRef = inject(MatDialogRef<SettingsSdlcInfoDialog>);

  protected websocketUrl = computed(() => {
    const projectId = this.projectService.selectedProjectId();
    return `${environment.sdlcUrl}/projects/${projectId}/websocket/github`;
  })

  onCancel() {
    this.dialogRef.close();
  }
}
