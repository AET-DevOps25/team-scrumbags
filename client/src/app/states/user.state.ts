import { Injectable, signal } from '@angular/core';
import { User } from '../models/user.model';

@Injectable({
  providedIn: 'root',
})
export class UserState {
  private readonly _allUser = signal<Map<string, User>>(new Map());

  readonly allUser = this._allUser.asReadonly();

  setAllUsers(users: User[]): void {
    const map = new Map<string, User>();
    for (const user of users) {
      map.set(user.id, user);
    }
    this._allUser.set(map);
  }

  getUserById(id: string): User | undefined {
    return this.allUser().get(id);
  }
}
