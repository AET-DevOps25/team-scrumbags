import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import Keycloak from 'keycloak-js';

@Component({
  selector: 'app-logout.component',
  imports: [CommonModule, MatDialogModule, MatButtonModule],
  templateUrl: './logout.component.html',
  styleUrl: './logout.component.scss',
})
export class LogoutDialog {
  private dialogRef = inject(MatDialogRef<LogoutDialog>);
  private readonly keycloak = inject(Keycloak);

  logout() {
    this.keycloak.logout();
  }

  onCancel(): void {
    this.dialogRef.close();
  }
}
