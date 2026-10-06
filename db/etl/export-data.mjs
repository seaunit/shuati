import fs from "node:fs";
import path from "node:path";
import pg from "pg";

const { Client, types } = pg;

// 保留 timestamptz 的微秒精度：让 pg 返回原始字符串，而不是 JS Date
types.setTypeParser(1184, (v) => v);
types.setTypeParser(1114, (v) => v);

function loadEnv() {
  const file = path.join(process.cwd(), ".env.local");
  const out = {};
  for (const line of fs.readFileSync(file, "utf8").split(/\r?\n/)) {
    const m = line.match(/^\s*([A-Za-z0-9_]+)\s*=\s*(.*)\s*$/);
    if (m) out[m[1]] = m[2].trim().replace(/^["']|["']$/g, "");
  }
  return out;
}

function quote(value) {
  const s = String(value)
    .replace(/\u0000/g, "")
    .replace(/\\/g, "\\\\")
    .replace(/'/g, "''");
  return "'" + s + "'";
}

function normalizeTimestamp(value) {
  const raw = String(value).trim().replace(/\+00(?::00)?$/, "").replace(/Z$/, "").trim();
  const m = raw.match(/^(\d{4}-\d{2}-\d{2})[ T](\d{2}:\d{2}:\d{2})(\.\d+)?/);
  if (!m) return raw;
  const frac = m[3] ? (m[3] + "000000").slice(0, 7) : ".000000";
  return m[1] + " " + m[2] + frac;
}

function formatValue(value, dataType) {
  if (value === null || value === undefined) return "NULL";
  switch (dataType) {
    case "boolean":
      return value ? "1" : "0";
    case "json":
    case "jsonb":
      return quote(typeof value === "string" ? value : JSON.stringify(value));
    case "timestamp with time zone":
    case "timestamp without time zone":
      return quote(normalizeTimestamp(value));
    case "date":
      return quote(String(value));
    case "smallint":
    case "integer":
    case "bigint":
    case "numeric":
    case "real":
    case "double precision":
      return String(value);
    default:
      return quote(String(value));
  }
}

function chunk(rows, size) {
  const out = [];
  for (let i = 0; i < rows.length; i += size) out.push(rows.slice(i, i + size));
  return out;
}

const env = loadEnv();
const client = new Client({
  connectionString: env.SUPABASE_DB_URL.split("?")[0],
  ssl: { rejectUnauthorized: false },
  connectionTimeoutMillis: 20000,
});
await client.connect();
await client.query("set time zone 'UTC'");

const schema = JSON.parse(
  fs.readFileSync(path.join(process.cwd(), "db", "_supabase_raw", "schema.json"), "utf8"),
);
const typeOf = new Map(
  schema.columns.map((c) => [c.table_name + "." + c.column_name, c.data_type]),
);

const queries = [
  {
    table: "profiles",
    columns: ["id", "email", "password_hash", "nickname", "role", "status", "created_at", "last_login_at"],
    types: ["uuid", "text", "text", "text", "text", "text", "timestamp with time zone", "timestamp with time zone"],
    sql: `select p.id, p.email, u.encrypted_password as password_hash, p.nickname,
                 p.role, p.status, p.created_at,
                 coalesce(p.last_login_at, u.last_sign_in_at) as last_login_at
            from public.profiles p
            left join auth.users u on u.id = p.id
           order by p.created_at`,
  },
  {
    table: "auth_user_archive",
    columns: ["id", "email", "encrypted_password", "email_confirmed_at", "created_at", "updated_at", "last_sign_in_at", "raw_user_meta_data", "raw_app_meta_data", "is_super_admin"],
    types: ["uuid", "text", "text", "timestamp with time zone", "timestamp with time zone", "timestamp with time zone", "timestamp with time zone", "jsonb", "jsonb", "boolean"],
    sql: `select id, email, encrypted_password, email_confirmed_at, created_at, updated_at,
                 last_sign_in_at, raw_user_meta_data, raw_app_meta_data, is_super_admin
            from auth.users order by created_at`,
  },
  { table: "bank", order: "id" },
  { table: "unit", order: "id" },
  { table: "question", order: "id" },
  { table: "practice_record", order: "id" },
  { table: "practice_session", order: "id" },
  { table: "appeal", order: "id" },
  { table: "ai_config", order: "id" },
  { table: "ai_call_log", order: "id" },
  { table: "import_task", order: "created_at, id" },
];

const lines = [
  "-- 由 Supabase PostgreSQL 导出的数据，自动生成，请勿手工编辑",
  "SET NAMES utf8mb4;",
  "SET time_zone = '+00:00';",
  "SET FOREIGN_KEY_CHECKS = 0;",
  "",
];

const summary = [];

for (const q of queries) {
  let columns = q.columns;
  let types = q.types;
  let sql = q.sql;

  if (!columns) {
    const meta = await client.query(
      `select column_name, data_type
         from information_schema.columns
        where table_schema = 'public' and table_name = $1
        order by ordinal_position`,
      [q.table],
    );
    columns = meta.rows.map((r) => r.column_name);
    types = meta.rows.map((r) => r.data_type);
    sql = `select * from public."${q.table}" order by ${q.order ?? "1"}`;
  }

  const result = await client.query(sql);
  const rows = result.rows;
  summary.push([q.table, rows.length]);

  for (const batch of chunk(rows, 200)) {
    const values = batch.map(
      (row) =>
        "(" +
        columns
          .map((col, i) => formatValue(row[col], types[i] ?? typeOf.get(q.table + "." + col)))
          .join(", ") +
        ")",
    );
    lines.push(
      `INSERT INTO \`${q.table}\` (${columns.map((c) => "`" + c + "`").join(", ")}) VALUES\n` +
        values.join(",\n") +
        ";",
    );
    lines.push("");
  }
}

lines.push("SET FOREIGN_KEY_CHECKS = 1;");

const outFile = path.join(process.cwd(), "db", "_supabase_raw", "02_data.sql");
fs.writeFileSync(outFile, lines.join("\n"), "utf8");

for (const [table, n] of summary) console.log(table + ": " + n);
console.log("written: " + outFile);

await client.end();
