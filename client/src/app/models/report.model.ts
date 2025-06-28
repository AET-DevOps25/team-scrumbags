export interface Report {
  id: string;
  name: string;
  periodStart: Date;
  periodEnd: Date;
  userIds: string[];
  content: string;
}
