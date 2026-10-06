import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "拾题 · 刷题",
  description: "清新文艺的刷题与题库管理工具",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="zh-CN">
      <body className="min-h-screen antialiased">{children}</body>
    </html>
  );
}