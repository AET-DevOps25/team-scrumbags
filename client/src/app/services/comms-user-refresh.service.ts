import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class CommsUserRefreshService {
  onRefreshUsers = new Subject();
}
