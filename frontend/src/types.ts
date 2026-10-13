export interface Subscription {
  id: string;
  name: string;
  category: string;
  owner: string;
  ownerEmail: string;
  cycle: "Monthly" | "Yearly";
  amount: number;
  renewal: string;
  lastUsed: string | null;
  active: boolean;
  reviewed: boolean;
  capabilities: string[];
  notes: string;
  pulse: "Yes" | "No" | null;
  source: string;
  version: number;
  unused: boolean;
  charges?: { id: string; amount: number; date: string }[];
  pulses?: { sent_to: string; answer: string | null; created_at: string }[];
}
export interface Domain {
  id: string;
  name: string;
  registrar: string;
  expires: string;
  auto: boolean;
  source: string;
}
export interface User {
  id: string;
  name: string;
  email: string;
  role: string;
  workspace: string;
  workspace_id: string;
}
export interface Metrics {
  total: number;
  waste: number;
  renewing: number;
  flagged: number;
  annualWaste?: number;
}
export interface Report {
  id: string;
  created_on: string;
  output_text: string;
  model: string;
  input_snapshot: Metrics;
}
export interface Notification {
  id: string;
  kind: string;
  subject: string;
  recipient: string;
  status: string;
  attempts: number;
  due_on: string;
  last_error: string | null;
}
export const money = (n: number) =>
  new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: n % 100 ? 2 : 0,
  }).format(n / 100);
export const monthly = (s: Subscription) =>
  s.cycle === "Yearly" ? Math.round(s.amount / 12) : s.amount;
export const date = (d: string | null) =>
  d
    ? new Date(d + "T12:00:00").toLocaleDateString("en-GB", {
        day: "2-digit",
        month: "short",
        year: "numeric",
      })
    : "Not recorded";
export const today = () =>
  new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Kolkata",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
export const days = (d: string) =>
  Math.round(
    (Date.parse(d + "T12:00:00") - Date.parse(today() + "T12:00:00")) /
      86400000,
  );
export const status = (s: Subscription) =>
  !s.active
    ? "Cancelled"
    : !s.reviewed
      ? "Needs review"
      : s.unused
        ? "Likely unused"
        : "Active";
export const overlap = (s: Subscription, all: Subscription[]) =>
  all.filter(
    (x) =>
      x.id !== s.id &&
      x.active &&
      x.reviewed &&
      s.capabilities.some((c) => x.capabilities.includes(c)),
  );
