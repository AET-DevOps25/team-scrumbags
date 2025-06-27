import { User } from "./user.model";

export interface Project {
  id: string;
  name: string;
  description: string;

  users: User[];
}
