import crypto from "node:crypto";

function key(): Buffer {
  const b64 = process.env.APP_AES_SECRET;
  if (!b64) throw new Error("缺少环境变量 APP_AES_SECRET");
  return Buffer.from(b64, "base64");
}

export function encrypt(plain: string): string {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv("aes-256-gcm", key(), iv);
  const encrypted = Buffer.concat([cipher.update(plain, "utf8"), cipher.final()]);
  const tag = cipher.getAuthTag();
  return Buffer.concat([iv, encrypted, tag]).toString("base64");
}

export function decrypt(encoded: string): string {
  const all = Buffer.from(encoded, "base64");
  const iv = all.subarray(0, 12);
  const tag = all.subarray(all.length - 16);
  const encrypted = all.subarray(12, all.length - 16);
  const decipher = crypto.createDecipheriv("aes-256-gcm", key(), iv);
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(encrypted), decipher.final()]).toString("utf8");
}

export function mask(key: string): string {
  if (!key || key.length < 8) return "****";
  return key.slice(0, 4) + "****" + key.slice(-4);
}