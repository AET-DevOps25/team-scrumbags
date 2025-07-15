export interface Message {
  id: string;
  timestamp: Date;
  userId: string;
  isGenerated: boolean;
  content: string;
  loading: boolean;
}
