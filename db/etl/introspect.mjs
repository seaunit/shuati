import fs from "node:fs";
import path from "node:path";
import pg from "pg";

const { Client } = pg;

function loadEnv() {
  const file = path.join(process.cwd(), ".env.local");
  const out = {};
  for (const line of fs.readFileSync(file, "utf8").split(/\r?\n/)) {
    const m = line.match(/^\s*([A-Za-z0-9_]+)\s*=\s*(.*)\s*$/);
    if (m) out[m[1]] = m[2].trim().replace(/^["']|["']$/g, "");
  }
  return out;
}

const outDir = path.join(process.cwd(), "db", "_supabase_raw");
fs.mkdirSync(outDir, { recursive: true });

const env = loadEnv();
const connectionString = env.SUPABASE_DB_URL.split("?")[0];
const client = new Client({
  connectionString,
  ssl: { rejectUnauthorized: false },
  connectionTimeoutMillis: 20000,
});

await client.connect();

const columns = await client.query(`
  select c.table_name, c.column_name, c.ordinal_position, c.data_type, c.udt_name,
         c.is_nullable, c.column_default, c.character_maximum_length,
         c.numeric_precision, c.numeric_scale, c.datetime_precision, c.is_identity
    from information_schema.columns c
   where c.table_schema = 'public'
   order by c.table_name, c.ordinal_position
`);

const constraints = await client.query(`
  select tc.table_name, tc.constraint_name, tc.constraint_type,
         kcu.column_name, kcu.ordinal_position,
         ccu.table_schema as ref_schema, ccu.table_name as ref_table,
         ccu.column_name as ref_column,
         rc.delete_rule, rc.update_rule
    from information_schema.table_constraints tc
    left join information_schema.key_column_usage kcu
      on kcu.constraint_schema = tc.constraint_schema
     and kcu.constraint_name = tc.constraint_name
    left join information_schema.constraint_column_usage ccu
      on ccu.constraint_schema = tc.constraint_schema
     and ccu.constraint_name = tc.constraint_name
    left join information_schema.referential_constraints rc
      on rc.constraint_schema = tc.constraint_schema
     and rc.constraint_name = tc.constraint_name
   where tc.table_schema = 'public'
   order by tc.table_name, tc.constraint_type, tc.constraint_name, kcu.ordinal_position
`);

const checks = await client.query(`
  select conname as constraint_name,
         conrelid::regclass::text as table_name,
         pg_get_constraintdef(oid) as definition
    from pg_constraint
   where connamespace = 'public'::regnamespace
     and contype = 'c'
   order by conrelid::regclass::text, conname
`);

const indexes = await client.query(`
  select schemaname, tablename, indexname, indexdef
    from pg_indexes
   where schemaname = 'public'
   order by tablename, indexname
`);

const authUserColumns = await client.query(`
  select column_name, ordinal_position, data_type, udt_name, is_nullable, column_default
    from information_schema.columns
   where table_schema = 'auth' and table_name = 'users'
   order by ordinal_position
`);

const authUsers = await client.query(`
  select id, email, encrypted_password, email_confirmed_at, created_at, updated_at,
         last_sign_in_at, raw_user_meta_data, raw_app_meta_data, is_super_admin
    from auth.users
   order by created_at
`);

const result = {
  generatedAt: new Date().toISOString(),
  server: (await client.query("select version() as v")).rows[0].v,
  columns: columns.rows,
  constraints: constraints.rows,
  checks: checks.rows,
  indexes: indexes.rows,
  authUserColumns: authUserColumns.rows,
  authUsers: authUsers.rows,
};

fs.writeFileSync(
  path.join(outDir, "schema.json"),
  JSON.stringify(result, null, 2),
  "utf8",
);

console.log("tables:", [...new Set(columns.rows.map((r) => r.table_name))].join(", "));
console.log("columns:", columns.rowCount, "constraints:", constraints.rowCount, "indexes:", indexes.rowCount);
console.log("authUsers:", authUsers.rowCount);

await client.end();
