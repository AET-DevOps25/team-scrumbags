import { Component } from '@angular/core';
import { SettingsSdlcTokenComponent } from './settings-sdlc-token/settings-sdlc-token.component';
import { SettingsSdlcUsersComponent } from './settings-sdlc-users/settings-sdlc-users.component';

@Component({
  selector: 'app-settings-sdlc',
  imports: [SettingsSdlcTokenComponent, SettingsSdlcUsersComponent],
  templateUrl: './settings-sdlc.component.html',
  styleUrl: './settings-sdlc.component.scss',
})
export class SdlcSettings {}
