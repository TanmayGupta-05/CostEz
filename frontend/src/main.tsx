import React, {
  useEffect,
  useRef,
  useState,
  type ReactNode,
  type FormEvent,
} from "react";
import { createRoot } from "react-dom/client";
import * as Dialog from "@radix-ui/react-dialog";
import {
  LayoutDashboard,
  List,
  Globe,
  FileText,
  Settings as SettingsIcon,
} from "lucide-react";
import { api, login, logout, token } from "./api";
import {
  type Subscription,
  type Domain,
  type User,
  type Report,
  type Metrics,
  type Notification,
  money,
  monthly,
  date,
  today,
  days,
  status,
  overlap,
} from "./types";
import "@fontsource/instrument-sans/400.css";
import "@fontsource/instrument-sans/500.css";
import "@fontsource/instrument-sans/600.css";
import "@fontsource/ibm-plex-mono/400.css";
import "@fontsource/ibm-plex-mono/500.css";
import "@fontsource/newsreader/500.css";
import "./style.css";
import "./extra.css";
const empty: Metrics = { total: 0, waste: 0, renewing: 0, flagged: 0 };
function Button({
  children,
  onClick,
  primary = false,
  danger = false,
  disabled = false,
  type = "button",
}: {
  children: ReactNode;
  onClick?: () => void;
  primary?: boolean;
  danger?: boolean;
  disabled?: boolean;
  type?: "button" | "submit";
}) {
  return (
    <button
      type={type}
      className={primary ? "primary" : danger ? "danger" : ""}
      onClick={onClick}
      disabled={disabled}
    >
      {children}
    </button>
  );
}
function Table({
  headers,
  rows,
  footer,
}: {
  headers: string[];
  rows: ReactNode[][];
  footer?: ReactNode;
}) {
  return (
    <div className="panel tablepanel">
      <table>
        <thead>
          <tr>
            {headers.map((h) => (
              <th key={h}>{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.length ? (
            rows.map((r, i) => (
              <tr key={i}>
                {r.map((v, j) => (
                  <td
                    key={j}
                    data-label={headers[j]}
                    className={
                      [
                        "Amount",
                        "Next renewal",
                        "Last used",
                        "Expires",
                        "Monthly equivalent",
                      ].includes(headers[j])
                        ? "number"
                        : ""
                    }
                  >
                    {v}
                  </td>
                ))}
              </tr>
            ))
          ) : (
            <tr>
              <td colSpan={headers.length} className="empty">
                No records match this view. Add a record or change the filters.
              </td>
            </tr>
          )}
        </tbody>
        {footer && (
          <tfoot>
            <tr>
              <td colSpan={headers.length}>{footer}</td>
            </tr>
          </tfoot>
        )}
      </table>
    </div>
  );
}
function Head({
  title,
  description,
  children,
}: {
  title: string;
  description: string;
  children?: ReactNode;
}) {
  return (
    <div className="pagehead">
      <div>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      <div className="actions">{children}</div>
    </div>
  );
}
function Badge({ s }: { s: Subscription }) {
  return (
    <span
      className={"status " + (s.unused ? "waste" : !s.reviewed ? "warn" : "ok")}
    >
      {status(s)}
    </span>
  );
}
function Modal({
  title,
  children,
  onClose,
  drawer = false,
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
  drawer?: boolean;
}) {
  return (
    <Dialog.Root open onOpenChange={(v) => !v && onClose()}>
      <Dialog.Portal>
        <Dialog.Overlay className="overlay" />
        <Dialog.Content
          className={"modal " + (drawer ? "drawer" : "")}
          aria-describedby={undefined}
        >
          <div className="dialoghead">
            <Dialog.Title asChild>
              <h2>{title}</h2>
            </Dialog.Title>
            <Dialog.Close asChild>
              <Button>Close</Button>
            </Dialog.Close>
          </div>
          {children}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
function App() {
  const [user, setUser] = useState<User | null>(null),
    [checking, setChecking] = useState(true),
    [page, setPage] = useState(location.hash.slice(1) || "overview"),
    [subs, setSubs] = useState<Subscription[]>([]),
    [domains, setDomains] = useState<Domain[]>([]),
    [metrics, setMetrics] = useState<Metrics>(empty),
    [reports, setReports] = useState<Report[]>([]),
    [notifications, setNotifications] = useState<Notification[]>([]),
    [integrations, setIntegrations] = useState({
      ai: false,
      email: false,
      gmail: false,
    }),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [message, setMessage] = useState(""),
    [modal, setModal] = useState<ReactNode>(null),
    [flagOnly, setFlagOnly] = useState(false),
    [detail, setDetail] = useState<Subscription | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const notify = (s: string) => {
    setMessage(s);
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => setMessage(""), 4000);
  };
  const load = async () => {
    const [u, s, d, m, r, n, i] = await Promise.all([
      api<User>("/me"),
      api<Subscription[]>("/subscriptions"),
      api<Domain[]>("/domains"),
      api<Metrics>("/overview"),
      api<Report[]>("/reports"),
      api<Notification[]>("/notifications"),
      api<typeof integrations>("/integrations"),
    ]);
    setUser(u);
    setSubs(s);
    setDomains(d);
    setMetrics(m);
    setReports(r);
    setNotifications(n);
    setIntegrations(i);
  };
  useEffect(() => {
    token()
      .then(() => api<User>("/me"))
      .then((u) => {
        setUser(u);
        return load();
      })
      .catch(() => setUser(null))
      .finally(() => setChecking(false));
    const change = () => {
      setPage(location.hash.slice(1) || "overview");
      setModal(null);
      setDetail(null);
    };
    window.addEventListener("hashchange", change);
    return () => window.removeEventListener("hashchange", change);
  }, []);
  const run = async (task: () => Promise<unknown>, success?: string) => {
    setBusy(true);
    setError("");
    try {
      const result = await task();
      await load();
      if (success) notify(success);
      return result;
    } catch (e) {
      setError((e as Error).message);
      throw e;
    } finally {
      setBusy(false);
    }
  };
  const safely = (task: () => Promise<unknown>, success?: string) => {
    void run(task, success).catch(() => {});
  };
  const showDetail = (s: Subscription) =>
    safely(async () => {
      setDetail(await api<Subscription>("/subscriptions/" + s.id));
    });
  const edit = (s?: Subscription) => {
    setDetail(null);
    setModal(
      <SubscriptionForm
        sub={s}
        busy={busy}
        onSave={async (v) => {
          await run(
            () =>
              api(
                "/subscriptions" + (s ? "/" + s.id : ""),
                s ? "PATCH" : "POST",
                v,
              ),
            "Subscription saved.",
          );
          setModal(null);
        }}
      />,
    );
  };
  const editDomain = (d?: Domain) =>
    setModal(
      <DomainForm
        domain={d}
        onSave={async (v) => {
          await run(
            () =>
              api("/domains" + (d ? "/" + d.id : ""), d ? "PATCH" : "POST", v),
            "Domain saved.",
          );
          setModal(null);
        }}
      />,
    );
  const confirm = (s: Subscription) =>
    safely(
      () => api("/subscriptions/" + s.id, "PATCH", { ...s, reviewed: true }),
      "Subscription confirmed.",
    );
  const ask = (s: Subscription) =>
    safely(async () => {
      const result = await api<{ url: string; emailEnabled: boolean }>(
        "/subscriptions/" + s.id + "/pulse",
        "POST",
      );
      setDetail(null);
      setModal(
        <>
          <p>
            {result.emailEnabled
              ? "The owner request is queued for delivery."
              : "SMTP is disabled. The request is saved and its email is shown as a preview in Settings."}
          </p>
          <p>Owner: {s.ownerEmail}</p>
          <a className="button" href={result.url}>
            Open owner response page
          </a>
          <p className="muted">
            The single-use link expires in 14 days and works on any device that
            can reach this server.
          </p>
        </>,
      );
    });
  const remove = (kind: "subscriptions" | "domains", id: string) =>
    setModal(
      <>
        <p>
          Delete this record and its associated history? This cannot be undone.
        </p>
        <div className="actions">
          <Button onClick={() => setModal(null)}>Keep record</Button>
          <Button
            danger
            onClick={() =>
              safely(async () => {
                await api("/" + kind + "/" + id, "DELETE");
                setModal(null);
                setDetail(null);
              }, "Record deleted.")
            }
          >
            Delete record
          </Button>
        </div>
      </>,
    );
  if (page.startsWith("pulse/"))
    return <Pulse tokenValue={page.split("/")[1]} />;
  if (checking)
    return (
      <div className="login">
        <h1>Costez</h1>
        <p>Loading your workspace…</p>
      </div>
    );
  if (!user) return <Auth onSignedIn={load} />;
  const routes = [
    ["overview", "Overview", LayoutDashboard],
    ["subscriptions", "Subscriptions", List],
    ["domains", "Domains", Globe],
    ["reports", "Reports", FileText],
    ["settings", "Settings", SettingsIcon],
  ] as const;
  return (
    <>
      <header>
        <a className="wordmark" href="#overview">
          Costez
        </a>
        <span id="workspace">{user.workspace}</span>
        <button
          className="ticker"
          onClick={() => {
            setFlagOnly(true);
            location.hash = "subscriptions";
          }}
        >
          {money(metrics.waste)} / month flagged across {metrics.flagged} tools
        </button>
        <Button
          onClick={() =>
            void logout()
              .then(() => {
                setUser(null);
                location.hash = "overview";
              })
              .catch((e) => setError(e.message))
          }
        >
          Sign out
        </Button>
      </header>
      <aside>
        <nav>
          {routes.map(([id, name, Icon]) => (
            <a key={id} href={"#" + id} className={page === id ? "active" : ""}>
              <Icon size={16} strokeWidth={1.5} />
              {name}
            </a>
          ))}
        </nav>
        <p>
          Spend visibility.
          <br />
          Decisions stay with you.
        </p>
      </aside>
      <main>
        {error && (
          <div className="notice" role="alert">
            {error} <Button onClick={() => setError("")}>Dismiss</Button>
          </div>
        )}
        {busy && (
          <p className="muted" role="status">
            Saving or refreshing workspace…
          </p>
        )}
        {page === "overview" && (
          <Overview subs={subs} metrics={metrics} onDetail={showDetail} />
        )}{" "}
        {page === "subscriptions" && (
          <Subscriptions
            subs={subs}
            flagOnly={flagOnly}
            clearFlag={() => setFlagOnly(false)}
            onDetail={showDetail}
            onEdit={edit}
            onConfirm={confirm}
            onImport={async (file) => {
              const form = new FormData();
              form.append("file", file);
              await run(async () => {
                const result = await api<{
                  imported: number;
                  errors: string[];
                }>("/imports/csv", "POST", form);
                setModal(
                  <>
                    <p>
                      {result.imported} rows imported as Needs review.{" "}
                      {result.errors.length} rows rejected.
                    </p>
                    {result.errors.map((e) => (
                      <p key={e}>{e}</p>
                    ))}
                    <p className="muted">
                      Repeated imports of identical files return the original
                      result without adding duplicates.
                    </p>
                  </>,
                );
              });
            }}
          />
        )}
        {page === "domains" && (
          <>
            <Head
              title="Domains"
              description="Track expiry dates and review auto-renew before it matters."
            >
              <Button primary onClick={() => editDomain()}>
                Add domain
              </Button>
            </Head>
            <Table
              headers={[
                "Domain",
                "Registrar",
                "Expires",
                "Auto-renew",
                "Status",
                "Actions",
              ]}
              rows={domains.map((d) => [
                d.name,
                <>
                  {d.registrar}
                  <br />
                  <span className="muted">
                    {d.source === "RDAP"
                      ? "Fetched from public registry"
                      : "Entered manually"}
                  </span>
                </>,
                date(d.expires),
                d.auto ? "On" : "Off",
                <span
                  className={
                    "status " +
                    (days(d.expires) < 0 || (days(d.expires) <= 60 && !d.auto)
                      ? "warn"
                      : "ok")
                  }
                >
                  {days(d.expires) < 0
                    ? "Expired"
                    : days(d.expires) <= 60 && !d.auto
                      ? "Renewal needs attention"
                      : "Tracked"}
                </span>,
                <div className="actions">
                  <Button onClick={() => editDomain(d)}>Edit</Button>
                  <Button
                    disabled={busy}
                    onClick={() =>
                      safely(
                        () => api("/domains/" + d.id + "/refresh", "POST"),
                        "Registry expiry updated.",
                      )
                    }
                  >
                    Refresh registry
                  </Button>
                  <Button danger onClick={() => remove("domains", d.id)}>
                    Delete
                  </Button>
                </div>,
              ])}
            />
            <p className="muted">
              Registry lookup supports .com, .net and .org. Other registries can
              be entered manually.
            </p>
          </>
        )}
        {page === "reports" && (
          <>
            <Head
              title="Reports"
              description="Computed numbers, readable summaries, inspectable snapshots."
            >
              <Button
                primary
                disabled={busy}
                onClick={() =>
                  safely(async () => {
                    const r = await api<{ warning: string }>(
                      "/reports",
                      "POST",
                    );
                    if (r.warning) notify(r.warning);
                  }, "Savings summary generated.")
                }
              >
                Generate savings summary
              </Button>
              <Button onClick={() => window.print()}>Print / save PDF</Button>
            </Head>
            <div className="notice">
              {integrations.ai
                ? "AI wording is enabled. Only aggregate financial figures are sent; no emails or owner details."
                : "AI is not configured. Reports use computed wording and remain fully available."}{" "}
              Maximum 10 reports per workspace per day.
            </div>
            {reports.length ? (
              reports.map((r) => (
                <section className="panel report" key={r.id}>
                  <h2>Savings summary · {date(r.created_on)}</h2>
                  <p>{r.output_text}</p>
                  <Table
                    headers={["Metric", "Amount"]}
                    rows={[
                      [
                        "Monthly equivalent spend",
                        money(r.input_snapshot.total),
                      ],
                      [
                        "Potentially unused monthly spend",
                        money(r.input_snapshot.waste),
                      ],
                      [
                        "Annualized potential savings",
                        money(
                          r.input_snapshot.annualWaste ||
                            r.input_snapshot.waste * 12,
                        ),
                      ],
                    ]}
                  />
                  <p className="muted">
                    Numbers computed by Costez. Wording{" "}
                    {r.model === "deterministic"
                      ? "computed by Costez"
                      : "generated by AI (" + r.model + ")"}
                    . Snapshot captured when generated.
                  </p>
                </section>
              ))
            ) : (
              <div className="panel">
                No reports yet. Generate a savings summary to review current
                spend.
              </div>
            )}
          </>
        )}
        {page === "settings" && (
          <Settings
            user={user}
            integrations={integrations}
            notifications={notifications}
            onRename={(name) =>
              run(
                () => api("/workspace", "PATCH", { name }),
                "Workspace saved.",
              )
            }
            onRun={() =>
              safely(
                () => api("/notifications/run", "POST"),
                "Upcoming reminders checked.",
              )
            }
            onDelete={() =>
              setModal(
                <>
                  <h3>Delete {user.workspace}?</h3>
                  <p>
                    All subscriptions, charges, domains, reports, owner requests
                    and workspace accounts will be permanently removed. Export
                    your data first.
                  </p>
                  <Button
                    danger
                    onClick={() => {
                      void api("/account", "DELETE")
                        .then(() => {
                          setModal(null);
                          setUser(null);
                        })
                        .catch((e) => setError(e.message));
                    }}
                  >
                    Permanently delete workspace
                  </Button>
                </>,
              )
            }
            onError={(e) => setError(e)}
          />
        )}
      </main>
      {modal && (
        <Modal title="Workspace action" onClose={() => setModal(null)}>
          {modal}
        </Modal>
      )}
      {detail && (
        <Modal title={detail.name} drawer onClose={() => setDetail(null)}>
          <Ledger
            s={detail}
            subs={subs}
            onEdit={() => edit(detail)}
            onAsk={() => ask(detail)}
            onDelete={() => remove("subscriptions", detail.id)}
            onCharge={async (amount) => {
              await run(
                () =>
                  api(
                    "/subscriptions/" + detail.id + "/charges",
                    "POST",
                    amount,
                  ),
                "Charge recorded.",
              );
              setDetail(await api("/subscriptions/" + detail.id));
            }}
          />
        </Modal>
      )}
      {message && (
        <div id="toast" style={{ display: "block" }} role="status">
          {message}
        </div>
      )}
    </>
  );
}
function Auth({ onSignedIn }: { onSignedIn: () => Promise<void> }) {
  const [register, setRegister] = useState(false),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [config, setConfig] = useState({ registration: true, demo: false });
  useEffect(() => {
    api<typeof config>("/auth/config")
      .then(setConfig)
      .catch(() => {});
  }, []);
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const data = Object.fromEntries(new FormData(e.currentTarget));
    setBusy(true);
    setError("");
    try {
      if (register) await api("/auth/register", "POST", data);
      await login(String(data.email), String(data.password));
      await onSignedIn();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="login">
      <a className="wordmark" href="#overview">
        Costez
      </a>
      <div className="panel">
        <h1>{register ? "Create a workspace" : "Sign in to your ledger"}</h1>
        <p>
          Track software spend, review potential waste, and see what renews
          next.
        </p>
        {error && (
          <p role="alert" className="waste">
            {error}
          </p>
        )}
        <form onSubmit={submit}>
          {register && (
            <>
              <label htmlFor="authname">Your name</label>
              <input
                id="authname"
                name="name"
                required
                maxLength={100}
                autoComplete="name"
              />
              <label htmlFor="authworkspace">Workspace name</label>
              <input
                id="authworkspace"
                name="workspace"
                required
                maxLength={100}
              />
            </>
          )}
          <label htmlFor="email">Email</label>
          <input
            id="email"
            name="email"
            type="email"
            required
            autoComplete="username"
          />
          <label htmlFor="password">Password</label>
          <input
            id="password"
            name="password"
            type="password"
            required
            minLength={register ? 12 : 1}
            maxLength={100}
            autoComplete={register ? "new-password" : "current-password"}
          />
          <div className="actions">
            <Button primary type="submit" disabled={busy}>
              {busy
                ? "Please wait…"
                : register
                  ? "Create workspace"
                  : "Sign in"}
            </Button>
            {config.registration && (
              <Button
                onClick={() => {
                  setRegister(!register);
                  setError("");
                }}
              >
                {register ? "Use existing account" : "Create workspace"}
              </Button>
            )}
          </div>
        </form>
        <p className="muted">
          No Gmail access is requested. Google sign-in and receipt scanning will
          be added later.
        </p>
      </div>
      <p className="muted">
        {config.demo
          ? "Local demo account: demo@costez.local. Use the demo password configured by your administrator."
          : "Your workspace data is stored on the server, separate from other workspaces."}
      </p>
    </div>
  );
}
function Overview({
  subs,
  metrics: m,
  onDetail,
}: {
  subs: Subscription[];
  metrics: Metrics;
  onDetail: (s: Subscription) => void;
}) {
  const live = subs.filter((s) => s.active && s.reviewed),
    soon = live.filter((s) => days(s.renewal) >= 0 && days(s.renewal) <= 90),
    max = Math.max(...soon.map((s) => s.amount), 1),
    attention = subs
      .filter(
        (s) => s.active && (!s.reviewed || s.unused || overlap(s, live).length),
      )
      .sort((a, b) => Number(b.unused) - Number(a.unused))
      .slice(0, 5),
    categories: Record<string, number> = {};
  live.forEach(
    (s) =>
      (categories[s.category] = (categories[s.category] || 0) + monthly(s)),
  );
  return (
    <>
      <Head
        title="Overview"
        description="Your software spend, with the next decision in view."
      >
        <a className="button" href="#subscriptions">
          View subscriptions
        </a>
      </Head>
      <div className="panel figures">
        <div className="figure">
          <span>Monthly equivalent spend</span>
          <strong>{money(m.total)}</strong>
          <span className="muted">Confirmed active subscriptions</span>
        </div>
        <div className="figure">
          <span>Flagged as potentially unused</span>
          <strong className="waste">{money(m.waste)}</strong>
          <span className="muted">
            {m.flagged} tools · review usage evidence
          </span>
        </div>
        <div className="figure">
          <span>Renewing in 30 days</span>
          <strong>{money(m.renewing)}</strong>
          <span className="muted">Actual upcoming invoice amounts</span>
        </div>
      </div>
      <section className="panel">
        <h2>Next 90 days</h2>
        <svg
          className="timeline"
          viewBox="0 0 1000 116"
          role="img"
          aria-label="Upcoming renewal amounts"
        >
          <line x1={10} x2={990} y1={85} y2={85} stroke="var(--line-strong)" />
          {[0, 30, 60, 90].map((n) => (
            <text key={n} x={10 + (n / 90) * 960} y={108}>
              {n === 0 ? "Today" : n + " days"}
            </text>
          ))}
          {soon.map((s) => (
            <line
              key={s.id}
              tabIndex={0}
              x1={10 + (days(s.renewal) / 90) * 960}
              x2={10 + (days(s.renewal) / 90) * 960}
              y1={85 - (60 * s.amount) / max}
              y2={85}
              stroke={days(s.renewal) <= 7 ? "var(--warn)" : "var(--accent)"}
              strokeWidth={5}
            >
              <title>
                {s.name} · {money(s.amount)} · {date(s.renewal)}
              </title>
            </line>
          ))}
        </svg>
        <p className="muted">
          Tick height shows invoice amount. Hover or focus for details.
        </p>
      </section>
      <section className="panel">
        <h2>Needs your attention</h2>
        {attention.map((s) => (
          <div className="attention" key={s.id}>
            <div>
              <button className="tool" onClick={() => onDetail(s)}>
                {s.name}
              </button>
              <p>
                {!s.reviewed
                  ? "Confirm imported details before including this tool in totals."
                  : s.unused
                    ? s.pulse === "No"
                      ? "Owner answered: No, we do not use it."
                      : "Last recorded use: " + date(s.lastUsed) + "."
                    : "Possible capability overlap with " +
                      overlap(s, live)
                        .map((x) => x.name)
                        .join(", ") +
                      ". Review plans before cancelling."}
              </p>
            </div>
            <span className={"number " + (s.unused ? "waste" : "")}>
              {money(monthly(s))}/mo
            </span>
          </div>
        ))}
        {!attention.length && (
          <p>
            No subscriptions need attention. Add a subscription to begin
            tracking spend.
          </p>
        )}
      </section>
      <section className="panel">
        <h2>Spend by category</h2>
        {Object.entries(categories)
          .sort((a, b) => b[1] - a[1])
          .map(([k, v]) => (
            <div className="barrow" key={k}>
              <span>{k}</span>
              <div
                className="bar"
                style={{
                  width:
                    (v / Math.max(...Object.values(categories))) * 100 + "%",
                }}
              />
              <span className="number">{money(v)}</span>
            </div>
          ))}
      </section>
    </>
  );
}
function Subscriptions({
  subs,
  flagOnly,
  clearFlag,
  onDetail,
  onEdit,
  onConfirm,
  onImport,
}: {
  subs: Subscription[];
  flagOnly: boolean;
  clearFlag: () => void;
  onDetail: (s: Subscription) => void;
  onEdit: (s?: Subscription) => void;
  onConfirm: (s: Subscription) => void;
  onImport: (f: File) => Promise<void>;
}) {
  const [search, setSearch] = useState(""),
    [filter, setFilter] = useState({
      status: "",
      category: "",
      owner: "",
      cycle: "",
    }),
    [error, setError] = useState("");
  const input = useRef<HTMLInputElement>(null);
  const selected = subs.filter(
    (s) =>
      (!flagOnly || s.unused) &&
      (s.name + " " + s.owner).toLowerCase().includes(search.toLowerCase()) &&
      (!filter.status || status(s) === filter.status) &&
      (!filter.category || filter.category === s.category) &&
      (!filter.owner || filter.owner === s.owner) &&
      (!filter.cycle || filter.cycle === s.cycle),
  );
  return (
    <>
      <Head
        title="Subscriptions"
        description={`${subs.length} records · yearly invoices normalized for monthly totals.`}
      >
        <a className="button" href="/api/imports/template" download>
          CSV template
        </a>
        <Button onClick={() => input.current?.click()}>Import CSV</Button>
        <Button primary onClick={() => onEdit()}>
          Add subscription
        </Button>
        <input
          ref={input}
          hidden
          type="file"
          accept=".csv"
          onChange={(e) => {
            const f = e.target.files?.[0];
            if (f) void onImport(f).catch((err) => setError(err.message));
            e.target.value = "";
          }}
        />
      </Head>
      {error && (
        <p className="waste" role="alert">
          {error}
        </p>
      )}
      {flagOnly && (
        <p>
          Showing potentially unused tools.{" "}
          <Button onClick={clearFlag}>Show all</Button>
        </p>
      )}
      <div className="filters">
        <input
          aria-label="Search subscriptions"
          placeholder="Search tool or owner"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        {(["status", "category", "owner", "cycle"] as const).map((k) => (
          <select
            key={k}
            aria-label={"Filter " + k}
            value={filter[k]}
            onChange={(e) => setFilter({ ...filter, [k]: e.target.value })}
          >
            <option value="">
              All {k === "status" ? "statuses" : k + "s"}
            </option>
            {(k === "status"
              ? ["Active", "Needs review", "Likely unused", "Cancelled"]
              : k === "cycle"
                ? ["Monthly", "Yearly"]
                : [...new Set(subs.map((s) => s[k]))]
            ).map((v) => (
              <option key={v}>{v}</option>
            ))}
          </select>
        ))}
      </div>
      <Table
        headers={[
          "Tool",
          "Category",
          "Owner",
          "Cycle",
          "Amount",
          "Next renewal",
          "Last used",
          "Status",
          "Actions",
        ]}
        rows={selected.map((s) => [
          <button className="tool" onClick={() => onDetail(s)}>
            {s.name}
          </button>,
          <span className="chip">{s.category}</span>,
          s.owner,
          s.cycle,
          <>
            {money(s.amount)}
            {s.cycle === "Yearly" && (
              <>
                <br />
                <span className="muted">
                  {money(monthly(s))}/mo (billed yearly)
                </span>
              </>
            )}
          </>,
          date(s.renewal),
          date(s.lastUsed),
          <Badge s={s} />,
          <div className="actions">
            {!s.reviewed && (
              <Button onClick={() => onConfirm(s)}>Confirm</Button>
            )}
            <Button onClick={() => onEdit(s)}>Edit</Button>
          </div>,
        ])}
        footer={
          <span className="number">
            Monthly equivalent of displayed confirmed active records:{" "}
            {money(
              selected
                .filter((s) => s.active && s.reviewed)
                .reduce((n, s) => n + monthly(s), 0),
            )}
          </span>
        }
      />
    </>
  );
}
function SubscriptionForm({
  sub: s,
  onSave,
}: {
  sub?: Subscription;
  busy?: boolean;
  onSave: (v: unknown) => Promise<void>;
}) {
  const [error, setError] = useState(""),
    [saving, setSaving] = useState(false);
  const submit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setSaving(true);
    setError("");
    const v = Object.fromEntries(new FormData(e.currentTarget));
    try {
      await onSave({
        ...s,
        name: v.name,
        category: v.category,
        owner: v.owner,
        ownerEmail: v.ownerEmail,
        cycle: v.cycle,
        amount: Math.round(Number(v.amount) * 100),
        renewal: v.renewal,
        lastUsed: v.lastUsed || null,
        active: v.active === "on",
        reviewed: true,
        capabilities: String(v.capabilities)
          .split(",")
          .map((x) => x.trim().toLowerCase())
          .filter(Boolean),
        notes: v.notes,
      });
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSaving(false);
    }
  };
  return (
    <form onSubmit={submit}>
      <h2>{s ? "Edit subscription" : "Add subscription"}</h2>
      {error && (
        <p className="waste" role="alert">
          {error}
        </p>
      )}
      <label htmlFor="toolname">Tool name</label>
      <input
        id="toolname"
        name="name"
        defaultValue={s?.name}
        required
        maxLength={100}
      />
      <label htmlFor="category">Category</label>
      <input
        id="category"
        name="category"
        defaultValue={s?.category || "Other"}
        required
        maxLength={80}
      />
      <label htmlFor="owner">Owner</label>
      <input
        id="owner"
        name="owner"
        defaultValue={s?.owner}
        required
        maxLength={100}
      />
      <label htmlFor="ownerEmail">Owner email</label>
      <input
        id="ownerEmail"
        name="ownerEmail"
        type="email"
        defaultValue={s?.ownerEmail || ""}
        maxLength={254}
      />
      <label htmlFor="amount">Invoice amount (INR)</label>
      <input
        id="amount"
        name="amount"
        type="number"
        min="0.01"
        max="100000000"
        step="0.01"
        defaultValue={s ? s.amount / 100 : ""}
        required
      />
      <label htmlFor="cycle">Billing cycle</label>
      <select id="cycle" name="cycle" defaultValue={s?.cycle || "Monthly"}>
        <option>Monthly</option>
        <option>Yearly</option>
      </select>
      <label htmlFor="renewal">Next renewal</label>
      <input
        id="renewal"
        name="renewal"
        type="date"
        defaultValue={s?.renewal || today()}
        required
      />
      <label htmlFor="lastUsed">Last recorded use (optional)</label>
      <input
        id="lastUsed"
        name="lastUsed"
        type="date"
        defaultValue={s?.lastUsed || ""}
        max={today()}
      />
      <label htmlFor="capabilities">Capabilities, comma separated</label>
      <input
        id="capabilities"
        name="capabilities"
        defaultValue={s?.capabilities.join(", ") || ""}
        maxLength={800}
        placeholder="video, chat, docs"
      />
      <label htmlFor="notes">Notes</label>
      <textarea
        id="notes"
        name="notes"
        defaultValue={s?.notes || ""}
        maxLength={4000}
      />
      <label className="check">
        <input
          type="checkbox"
          name="active"
          defaultChecked={s?.active ?? true}
        />{" "}
        Active subscription
      </label>
      <div className="actions">
        <Button primary type="submit" disabled={saving}>
          {saving ? "Saving…" : "Save subscription"}
        </Button>
      </div>
    </form>
  );
}
function DomainForm({
  domain: d,
  onSave,
}: {
  domain?: Domain;
  onSave: (v: unknown) => Promise<void>;
}) {
  const [error, setError] = useState(""),
    [saving, setSaving] = useState(false);
  return (
    <form
      onSubmit={async (e) => {
        e.preventDefault();
        setSaving(true);
        const v = Object.fromEntries(new FormData(e.currentTarget));
        try {
          await onSave({ ...v, auto: v.auto === "on" });
        } catch (e) {
          setError((e as Error).message);
        } finally {
          setSaving(false);
        }
      }}
    >
      <h2>{d ? "Edit domain" : "Add domain"}</h2>
      {error && <p className="waste">{error}</p>}
      <label htmlFor="domainname">Domain name</label>
      <input
        id="domainname"
        name="name"
        required
        defaultValue={d?.name}
        placeholder="example.com"
        maxLength={253}
      />
      <label htmlFor="registrar">Registrar</label>
      <input
        id="registrar"
        name="registrar"
        required
        defaultValue={d?.registrar}
        maxLength={100}
      />
      <label htmlFor="expires">Expiry date</label>
      <input
        id="expires"
        name="expires"
        type="date"
        defaultValue={d?.expires || today()}
        required
      />
      <label className="check">
        <input name="auto" type="checkbox" defaultChecked={d?.auto} />{" "}
        Auto-renew enabled
      </label>
      <div className="actions">
        <Button primary type="submit" disabled={saving}>
          Save domain
        </Button>
      </div>
    </form>
  );
}
function Ledger({
  s,
  subs,
  onEdit,
  onAsk,
  onDelete,
  onCharge,
}: {
  s: Subscription;
  subs: Subscription[];
  onEdit: () => void;
  onAsk: () => void;
  onDelete: () => void;
  onCharge: (v: { amount: number; date: string }) => Promise<void>;
}) {
  const [error, setError] = useState(""),
    [saving, setSaving] = useState(false);
  const matching = overlap(s, subs);
  return (
    <>
      <p>
        <Badge s={s} /> · {s.source}
      </p>
      <p className="number">
        {money(s.amount)} / {s.cycle.toLowerCase()}
      </p>
      <h2>Charge ledger</h2>
      {s.charges?.length ? (
        s.charges.map((c) => (
          <div className="ledger" key={c.id}>
            <span>{s.name}</span>
            <span className="leader" />
            <span>{date(c.date)}</span>
            <span className="number">{money(c.amount)}</span>
          </div>
        ))
      ) : (
        <p>No charges recorded.</p>
      )}
      <form
        onSubmit={async (e) => {
          e.preventDefault();
          const f = e.currentTarget,
            v = Object.fromEntries(new FormData(f));
          setSaving(true);
          try {
            await onCharge({
              amount: Math.round(Number(v.amount) * 100),
              date: String(v.date),
            });
            f.reset();
          } catch (e) {
            setError((e as Error).message);
          } finally {
            setSaving(false);
          }
        }}
      >
        <label htmlFor="charge">Record invoice amount (INR)</label>
        <input
          id="charge"
          name="amount"
          type="number"
          step="0.01"
          min="0.01"
          max="100000000"
          required
        />
        <label htmlFor="chargedate">Invoice date</label>
        <input
          id="chargedate"
          name="date"
          type="date"
          required
          defaultValue={today()}
        />
        <div className="actions">
          <Button type="submit" disabled={saving}>
            Record charge
          </Button>
        </div>
      </form>
      {error && <p className="waste">{error}</p>}
      <h2 className="spaced">Usage evidence</h2>
      <p>
        Last recorded use: {date(s.lastUsed)}.<br />
        Owner response: {s.pulse || "Not requested"}.
      </p>
      <Button primary onClick={onAsk}>
        Ask owner: still using this?
      </Button>
      {s.pulses?.map((p, i) => (
        <p key={i} className="muted">
          {p.sent_to} · {p.answer || "Awaiting response"}
        </p>
      ))}
      <h2 className="spaced">Possible overlap</h2>
      <p>
        {matching.length
          ? matching.map((x) => x.name).join(", ") +
            " shares a capability. Review requirements before cancelling."
          : "No matching capabilities found."}
      </p>
      <h2>Notes</h2>
      <p className="prewrap">{s.notes || "No notes yet."}</p>
      <div className="actions">
        <Button onClick={onEdit}>Edit details</Button>
        <Button danger onClick={onDelete}>
          Delete subscription
        </Button>
      </div>
    </>
  );
}
function Settings({
  user,
  integrations,
  notifications,
  onRename,
  onRun,
  onDelete,
  onError,
}: {
  user: User;
  integrations: { ai: boolean; email: boolean; gmail: boolean };
  notifications: Notification[];
  onRename: (name: string) => Promise<unknown>;
  onRun: () => void;
  onDelete: () => void;
  onError: (e: string) => void;
}) {
  return (
    <>
      <Head
        title="Settings"
        description="Workspace, integrations, reminders, and privacy."
      />
      <section className="panel">
        <h2>Workspace</h2>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            void onRename(
              String(new FormData(e.currentTarget).get("name")),
            ).catch(() => {});
          }}
        >
          <label htmlFor="workspaceName">Workspace name</label>
          <input
            id="workspaceName"
            name="name"
            defaultValue={user.workspace}
            required
            maxLength={100}
          />
          <div className="actions">
            <Button primary type="submit">
              Save workspace
            </Button>
          </div>
        </form>
        <p className="muted">Signed in as {user.email} · INR · Asia/Kolkata</p>
      </section>
      <section className="panel">
        <h2>Integrations and privacy</h2>
        <p>
          <span className="status warn">Gmail deferred</span> · Add
          subscriptions manually or import CSV.
        </p>
        <p>
          <span className={"status " + (integrations.ai ? "ok" : "warn")}>
            AI {integrations.ai ? "configured" : "not configured"}
          </span>{" "}
          · Reports fall back to computed summaries.
        </p>
        <p>
          <span className={"status " + (integrations.email ? "ok" : "warn")}>
            SMTP {integrations.email ? "enabled" : "preview mode"}
          </span>{" "}
          · Credentials are configured on the server.
        </p>
        <p>
          Subscriptions, charges, domains, owner replies and report snapshots
          are stored in the database. No Gmail bodies are read or stored. If AI
          is configured, only aggregate amounts are sent to OpenAI for report
          wording. Public owner links contain random single-use tokens; only
          their hashes are stored.
        </p>
        <div className="actions">
          <Button
            onClick={() => {
              void api("/export")
                .then((data) => {
                  const link = document.createElement("a");
                  link.href = URL.createObjectURL(
                    new Blob([JSON.stringify(data, null, 2)], {
                      type: "application/json",
                    }),
                  );
                  link.download = "costez-export.json";
                  link.click();
                  setTimeout(() => URL.revokeObjectURL(link.href), 1000);
                })
                .catch((e) => onError(e.message));
            }}
          >
            Export workspace JSON
          </Button>
          <Button danger onClick={onDelete}>
            Delete workspace
          </Button>
        </div>
      </section>
      <Head
        title="Reminder queue"
        description="Daily checks run at 08:00 IST. Email delivery retries up to five times."
      >
        <Button onClick={onRun}>Check reminders now</Button>
      </Head>
      <Table
        headers={["Subject", "Recipient", "Due date", "Delivery", "Attempts"]}
        rows={notifications.map((n) => [
          n.subject,
          n.recipient,
          date(n.due_on),
          <>
            {n.status}
            {n.last_error && <p className="muted">{n.last_error}</p>}
          </>,
          n.attempts,
        ])}
      />
      <p className="muted">
        Preview mode stores reminders without sending emails. Delivery is at
        least once; a crash after SMTP acceptance may cause a duplicate.
      </p>
    </>
  );
}
function Pulse({ tokenValue }: { tokenValue: string }) {
  const [pulse, setPulse] = useState<{
      name: string;
      answered: boolean;
    } | null>(null),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  useEffect(() => {
    api<{ name: string; answered: boolean }>(
      "/pulse/" + encodeURIComponent(tokenValue),
    )
      .then(setPulse)
      .catch((e) => setError(e.message));
  }, [tokenValue]);
  const answer = async (value: string) => {
    setBusy(true);
    setError("");
    try {
      await api(
        "/pulse/" + encodeURIComponent(tokenValue) + "/answer",
        "POST",
        { answer: value },
      );
      setPulse((p) => (p ? { ...p, answered: true } : p));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="login">
      <div className="panel">
        <h1>Owner usage check</h1>
        {error && (
          <p className="waste" role="alert">
            {error}
          </p>
        )}
        {pulse ? (
          pulse.answered ? (
            <p>Your response has been recorded. You can close this page.</p>
          ) : (
            <>
              <p>Are you still using {pulse.name}?</p>
              <div className="actions">
                <Button
                  primary
                  disabled={busy}
                  onClick={() => void answer("Yes")}
                >
                  Yes, we use it
                </Button>
                <Button disabled={busy} onClick={() => void answer("No")}>
                  No, we do not
                </Button>
              </div>
            </>
          )
        ) : (
          !error && <p>Loading request…</p>
        )}
      </div>
    </div>
  );
}
createRoot(document.getElementById("root")!).render(<App />);
