import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../environment';
import { User } from '../models/user.model';
import { handleError } from './api-utils';

@Injectable({
  providedIn: 'root',
})
export class UserApi {
  private http = inject(HttpClient);

  getAllUsers(): Observable<User[]> {
    return this.http
      .get<User[]>(`${environment.projectManagementUrl}/users`)
      .pipe(catchError(handleError('Error fetching project list')));
  }
}
