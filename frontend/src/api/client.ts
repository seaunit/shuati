export interface ApiEnvelope<T> {
  code: number;
  message: string;
  data: T;
}

export interface ApiOptions {
  // 游客模式：探测当前登录态的请求（如 /api/me）未登录时不要强制跳转登录页
  redirectOn401?: boolean;
}

/**
 * 带上 HTTP 状态码的接口错误。
 * 排障时 4xx（参数/权限）和 5xx（服务端异常）的处理方向完全不同，
 * 所以把状态码一路带到调用方，界面上能看到、用户截图也能直接定位。
 */
export class ApiRequestError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = "ApiRequestError";
    this.status = status;
  }
}

export async function api<T>(
  path: string,
  init: RequestInit = {},
  options: ApiOptions = {},
): Promise<T> {
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
    if (
      response.status === 401
      && options.redirectOn401 !== false
      && !path.startsWith("/api/auth")
    ) {
      if (window.location.pathname !== "/login") {
        window.location.href = "/login";
      }
    }
    throw new ApiRequestError(json.message || "请求失败", response.status);
  }
  return json.data;
}
