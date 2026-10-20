let token = "",
  headerName = "X-CSRF-TOKEN";
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}
export async function csrf() {
  const r = await fetch("/api/csrf", { credentials: "same-origin" });
  if (!r.ok) throw new Error("无法建立安全会话");
  const v = await r.json();
  token = v.token;
  headerName = v.headerName;
}
export async function api<T>(
  url: string,
  options: RequestInit = {},
): Promise<T> {
  const method = options.method || "GET";
  if (method !== "GET" && !token) await csrf();
  const headers = new Headers(options.headers);
  if (options.body && !(options.body instanceof URLSearchParams))
    headers.set("Content-Type", "application/json");
  if (method !== "GET") headers.set(headerName, token);
  const response = await fetch("/api" + url, {
    ...options,
    headers,
    credentials: "same-origin",
  });
  if (!response.ok) {
    if (response.status === 401 && url !== "/login")
      window.dispatchEvent(new Event("session-expired"));
    const body = await response.json().catch(() => ({}));
    throw new ApiError(response.status, body.message || "请求失败，请稍后重试");
  }
  if (response.status === 204) return undefined as T;
  return response.json();
}
export async function login(username: string, password: string) {
  await csrf();
  await api("/login", {
    method: "POST",
    body: new URLSearchParams({ username, password }),
  });
  await csrf();
}
export async function logout() {
  await api("/logout", { method: "POST" });
  token = "";
}
