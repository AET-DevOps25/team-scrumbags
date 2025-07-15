export interface Report {
  id: string;
  projectId: string;
  name: string;
  startTime: Date;
  endTime: Date;
  userIds: string[];
  generatedAt: Date;
  loading: boolean;
  summary: string;
}
