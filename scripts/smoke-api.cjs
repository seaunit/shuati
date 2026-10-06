const { req } = require("./env");

const URL = req("NEXT_PUBLIC_SUPABASE_URL");
const REF = new URL(URL).hostname.split(".")[0];
const ANON = req("NEXT_PUBLIC_SUPABASE_ANON_KEY");
const EMAIL = process.env.SMOKE_EMAIL || "shuati.admin@gmail.com";
const PASSWORD = req("SMOKE_PASSWORD");
const BASE_URL = process.env.SMOKE_BASE_URL || "http://localhost:3000";

(async () => {
  const login = await fetch(URL + "/auth/v1/token?grant_type=password", {
    method: "POST",
    headers: { "Content-Type": "application/json", apikey: ANON },
    body: JSON.stringify({ email: EMAIL, password: PASSWORD }),
  });
  const sess = await login.json();
  if (!sess.access_token) { console.log("LOGIN_FAIL", JSON.stringify(sess)); return; }

  const cookieValue = Buffer.from(JSON.stringify({
    access_token: sess.access_token,
    token_type: sess.token_type,
    expires_in: sess.expires_in,
    expires_at: sess.expires_at ?? Math.floor(Date.now() / 1000 + sess.expires_in),
    refresh_token: sess.refresh_token,
    user: sess.user,
  })).toString("base64url");

  const cookie = `sb-${REF}-auth-token=base64-${cookieValue}`;
  for (const path of ["/api/me", "/api/banks", "/api/stats/me", "/api/admin/banks", "/api/admin/stats/overview"]) {
    const r = await fetch(BASE_URL + path, { headers: { Cookie: cookie } });
    const body = await r.text();
    console.log(path + " => " + r.status + " " + body.slice(0, 200));
  }
})().catch((e) => console.log("ERR", e.message));
