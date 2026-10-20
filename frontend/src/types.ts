export interface Zone {
  id: string;
  name: string;
  description: string;
  readOnly: boolean;
  primary: string;
  secondaries: string[];
  serial?: number;
  recordCount?: number;
  error?: string;
}
export interface Rrset {
  name: string;
  type: string;
  ttl: number;
  values: string[];
  revision: string;
  editable: boolean;
  protection?: string;
}
export interface ZoneRecords {
  zone: Zone;
  records: Rrset[];
}
export interface User {
  username: string;
  admin: boolean;
}
export interface Meta {
  name: string;
  version: string;
  demo: boolean;
}
export interface AuditEvent {
  id: number;
  occurredAt: string;
  actor: string;
  zoneId: string;
  action: string;
  recordName: string;
  recordType: string;
  beforeJson: string;
  afterJson: string;
  status: string;
  detail: string;
}
export interface ProbeResult {
  server: string;
  reachable: boolean;
  rcode?: string;
  serial?: number;
  answers: string[];
  elapsedMs: number;
  error?: string;
}
