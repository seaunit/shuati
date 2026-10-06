const { createClient } = require("@supabase/supabase-js");
const { req } = require("./env");

(async () => {
  const admin = createClient(req("NEXT_PUBLIC_SUPABASE_URL"), req("SUPABASE_SERVICE_ROLE_KEY"), {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const email = process.env.ADMIN_EMAIL || "shuati.admin@gmail.com";
  const password = req("ADMIN_PASSWORD");
  let userId;

  const { data } = await admin.auth.admin.listUsers({ perPage: 1000 });
  const list = Array.isArray(data) ? data : (data && data.users ? data.users : []);
  const existing = list.find((u) => u.email === email);
  if (existing) {
    userId = existing.id;
    const { error } = await admin.auth.admin.updateUserById(userId, { password, email_confirm: true });
    if (error) throw error;
    console.log("ADMIN_UPDATED", userId);
  } else {
    const { data: created, error } = await admin.auth.admin.createUser({ email, password, email_confirm: true });
    if (error) throw error;
    userId = created.user.id;
    console.log("ADMIN_CREATED", userId);
  }

  const { error: pErr } = await admin.from("profiles").upsert({
    id: userId, email, nickname: "shuati.admin", role: "ADMIN", status: "ENABLED",
  });
  if (pErr) throw pErr;
  console.log("PROFILE_OK");
})().catch((e) => { console.error("ADMIN_ERR", e.message); process.exit(1); });
