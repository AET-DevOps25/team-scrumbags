import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../environment';
import { CommsPlatformConnection } from '../models/comms-platform-connection.model';
import { handleError } from './api-utils';
import { CommsUserMapping } from '../models/comms-users.model';

@Injectable({
  providedIn: 'root'
})
export class CommsApi {
  private http = inject(HttpClient);

  getAllCommsUsers(projectId: string): Observable<CommsUserMapping[]> {
    return this.http
      .get<CommsUserMapping[]>(`${environment.communicationUrl}/projects/${projectId}/comms/users`)
      .pipe(catchError(handleError('Error fetching user mappings')));
  }

  saveUserMapping(projectId: string, userMapping: CommsUserMapping): Observable<CommsUserMapping> {
    const params = new HttpParams()
      .set('userId', userMapping.userId)
      .set('platformUserId', userMapping.platformUserId);
    
    return this.http
      .post<CommsUserMapping>(
        `${environment.communicationUrl}/projects/${projectId}/comms/${userMapping.platform}/users`,
        {}, 
        { params }
      )
      .pipe(catchError(handleError('Error saving user mapping')));
  }

  addCommsConnection(projectId: string, serverId: string, platform: string): Observable<CommsPlatformConnection[]> {
    const params = new HttpParams()
      .set('serverId', serverId);
    
    return this.http
      .post<CommsPlatformConnection[]>(
        `${environment.communicationUrl}/projects/${projectId}/comms/${platform.toUpperCase()}/connections`,
        {},
        { params }
      )
      .pipe(catchError(handleError('Error adding communication connection')));
  }
}
