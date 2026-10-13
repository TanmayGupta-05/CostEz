import { describe, it, expect } from "vitest";
import { monthly, overlap, status, type Subscription } from "./types";
const sub = (
  id: string,
  category: string,
  capabilities: string[],
): Subscription => ({
  id,
  name: id,
  category,
  capabilities,
  owner: "Priya",
  ownerEmail: "",
  amount: 499900,
  cycle: "Yearly",
  renewal: "2026-11-01",
  lastUsed: null,
  active: true,
  reviewed: true,
  notes: "",
  pulse: null,
  source: "MANUAL",
  version: 0,
  unused: false,
});
describe("spend and classification", () => {
  it("normalizes annual invoices to paise", () =>
    expect(monthly(sub("a", "Design", []))).toBe(41658));
  it("finds video overlap across categories without self matches", () => {
    const a = sub("Zoom", "Video", ["video"]),
      b = sub("Workspace", "Office", ["video", "email"]);
    expect(overlap(a, [a, b])).toEqual([b]);
  });
  it("preserves lifecycle and review before flags", () => {
    const s = sub("a", "Other", []);
    s.unused = true;
    s.reviewed = false;
    expect(status(s)).toBe("Needs review");
    s.active = false;
    expect(status(s)).toBe("Cancelled");
  });
});
