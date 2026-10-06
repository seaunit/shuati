const fs = require("fs");
const path = require("path");
const { Client } = require("pg");
const { req } = require("./env");

(async () => {
  const sql = fs.readFileSync(path.join(__dirname, "..", "supabase", "migrations", "20260814200000_import_task.sql"), "utf8");
  const client = new Client({
    connectionString: req("SUPABASE_DB_URL"),
    ssl: { rejectUnauthorized: false },
    connectionTimeoutMillis: 20000,
  });
  await client.connect();
  try {
    await client.query(sql);
    console.log("MIGRATION_OK");
  } finally {
    await client.end();
  }
})().catch((e) => { console.error("MIGRATION_ERR", e.message); process.exit(1); });
