import { Component } from '@angular/core';
import { CommsSettingsServerId } from './settings-comms-server-id/settings-comms-server-id.component';
import { CommsSettingsUsers } from './settings-comms-users/settings-comms-users.component';

@Component({
  selector: 'app-settings-comms',
  imports: [CommsSettingsServerId, CommsSettingsUsers],
  templateUrl: './settings-comms.component.html',
  styleUrl: './settings-comms.component.scss'
})
export class CommsSettings {}
