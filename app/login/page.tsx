"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Leaf, Loader2 } from "lucide-react";
import { createClient } from "@/lib/supabase/client";

function messageOf(err: { code?: string; message?: string } | null) {
  const code = err?.code ?? "";
  const msg = (err?.message ?? "").toLowerCase();
  if (code === "user_already_exists" || msg.includes("already registered")) {
    return "该邮箱已注册，请直接登录";
  }
  if (code === "invalid_credentials" || msg.includes("invalid login credentials")) {
    return "邮箱或密码错误";
  }
  if (code === "weak_password" || msg.includes("password") && msg.includes("least")) {
    return "密码强度不足，请至少使用 6 位";
  }
  if (code === "over_request_rate_limit" || msg.includes("rate limit")) {
    return "操作过于频繁，请稍后再试";
  }
  return err?.message || "操作失败，请重试";
}

export default function LoginPage() {
  const router = useRouter();
  const supabase = createClient();
  const [mode, setMode] = useState<"login" | "register">("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    if (!email.trim() || !password) {
      setError("请填写邮箱和密码");
      return;
    }
    if (mode === "register" && password !== confirm) {
      setError("两次输入的密码不一致");
      return;
    }
    setLoading(true);
    try {
      if (mode === "login") {
        const { error } = await supabase.auth.signInWithPassword({
          email: email.trim(),
          password,
        });
        if (error) throw error;
      } else {
        const { error } = await supabase.auth.signUp({
          email: email.trim(),
          password,
        });
        if (error) throw error;
      }
      router.push(new URLSearchParams(window.location.search).get("next") || "/app");
      router.refresh();
    } catch (err) {
      setError(messageOf(err as { code?: string; message?: string }));
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="min-h-screen flex items-center justify-center px-6">
      <div className="w-full max-w-md">
        <div className="mb-8 text-center">
          <div className="inline-flex h-12 w-12 items-center justify-center rounded-2xl bg-moss/15 text-moss">
            <Leaf className="h-6 w-6" />
          </div>
          <h1 className="mt-4 text-2xl font-semibold tracking-wide text-ink">拾题</h1>
          <p className="mt-1 text-sm text-oat">安静地练习，温柔地记住</p>
        </div>

        <form onSubmit={submit} className="rounded-3xl border border-mist bg-white/70 p-8 shadow-sm backdrop-blur">
          <div className="mb-6 flex rounded-xl bg-mist p-1">
            {(["login", "register"] as const).map((m) => (
              <button
                key={m}
                type="button"
                onClick={() => {
                  setMode(m);
                  setError("");
                }}
                className={`flex-1 rounded-lg py-2 text-sm transition ${
                  mode === m ? "bg-white text-ink shadow-sm" : "text-oat"
                }`}
              >
                {m === "login" ? "登录" : "注册"}
              </button>
            ))}
          </div>

          <label className="mb-5 block">
            <span className="mb-1.5 block text-sm text-ink">邮箱</span>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full rounded-xl border border-mist bg-paper px-4 py-2.5 outline-none transition focus:border-moss"
              placeholder="you@example.com"
            />
          </label>

          <label className="mb-5 block">
            <span className="mb-1.5 block text-sm text-ink">密码</span>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full rounded-xl border border-mist bg-paper px-4 py-2.5 outline-none transition focus:border-moss"
              placeholder="至少 6 位"
            />
          </label>

          {mode === "register" && (
            <label className="mb-5 block">
              <span className="mb-1.5 block text-sm text-ink">确认密码</span>
              <input
                type="password"
                value={confirm}
                onChange={(e) => setConfirm(e.target.value)}
                className="w-full rounded-xl border border-mist bg-paper px-4 py-2.5 outline-none transition focus:border-moss"
                placeholder="再次输入密码"
              />
            </label>
          )}

          {error && (
            <p className="mb-4 rounded-xl bg-rose/10 px-4 py-2.5 text-sm text-rose">{error}</p>
          )}

          <button
            type="submit"
            disabled={loading}
            className="flex w-full items-center justify-center gap-2 rounded-xl bg-ink py-2.5 text-sm text-white transition hover:bg-ink/90 disabled:opacity-60"
          >
            {loading ? (
              <>
                <Loader2 className="h-4 w-4 animate-spin" />
                请稍候…
              </>
            ) : mode === "login" ? "登录" : "注册并登录"}
          </button>
        </form>

        <p className="mt-6 text-center text-xs text-oat">
          注册即表示你已阅读并同意使用规则
        </p>
      </div>
    </main>
  );
}