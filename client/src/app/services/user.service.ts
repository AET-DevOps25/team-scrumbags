import { inject, Injectable, signal } from '@angular/core';
import { finalize, Observable, tap } from 'rxjs';
import { UserState } from '../states/user.state';
import { User } from '../models/user.model';
import { UserApi } from './user.api';

@Injectable({
  providedIn: 'root',
})
export class UserService {
  private api = inject(UserApi);
  private state = inject(UserState)

  private _isLoadingAllUsers = signal<boolean>(false);
  public isLoadingAllUsers = this._isLoadingAllUsers.asReadonly();

  constructor() {  }

  public loadAllUsers(): Observable<User[]> {
    this._isLoadingAllUsers.set(true);
    return this.api.getAllUsers().pipe(
      tap((allUser) => {
        this.state.setAllUsers(allUser);
      }),
      finalize(() => this._isLoadingAllUsers.set(false))
    );
  }
}
