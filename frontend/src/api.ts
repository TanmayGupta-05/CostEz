let csrf = "";
export async function token() {
  const r = await fetch("/api/auth/csrf", { credentials: "same-origin" });
  const data = await r.json();
  csrf = data.token;
  return csrf;
}
export async function api<T = any>(
  path: string,
  method = "GET",
  body?: unknown,
): Promise<T> {
  if (method !== "GET" && !csrf) await token();
  const form = body instanceof FormData || body instanceof URLSearchParams;
  const res = await fetch("/api" + path, {
    method,
    credentials: "same-origin",
    headers: {
      ...(method !== "GET" ? { "X-XSRF-TOKEN": csrf } : {}),
      ...(!form && body ? { "Content-Type": "application/json" } : {}),
    },
    body:
      body === undefined
        ? undefined
        : form
          ? (body as BodyInit)
          : JSON.stringify(body),
  });
  if (!res.ok) {
    let message = "Request failed. Try again.";
    try {
      message = (await res.json()).message || message;
    } catch {}
    if (res.status === 401) message = "Please sign in to continue.";
    if (res.status === 403) {
      csrf = "";
      message = "Your session security token expired. Try again.";
    }
    throw Error(message);
  }
  if (res.status === 204 || res.headers.get("content-length") === "0")
    return undefined as T;
  const text = await res.text();
  return text ? JSON.parse(text) : (undefined as T);
}
export async function login(email: string, password: string) {
  await token();
  await api("/auth/login", "POST", new URLSearchParams({ email, password }));
  await token();
}
export async function logout() {
  await api("/auth/logout", "POST");
  csrf = "";
}
