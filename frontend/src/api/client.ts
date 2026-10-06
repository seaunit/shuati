export interface ApiEnvelope<T> {
  code: number;
  message: string;
  data: T;
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers ?? {});
  headers.set("X-Requested-With", "ShuatiApp");
  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(path, { ...init, headers, credentials: "include" });
  const json = (await response.json().catch(() => ({
    code: response.status,
    message: "请求失败",
    data: null,
  }))) as ApiEnvelope<T>;

  if (!response.ok || json.code !== 200) {
    if (response.status === 401 && !path.startsWith("/api/auth")) {
      if (window.location.pathname !== "/login") {
        window.location.href = "/login";
      }
    }
    throw new Error(json.message || "请求失败");
  }
  return json.data;
}
