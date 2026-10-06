const { Client } = require("pg");
const { req } = require("./env");

(async () => {
  const c = new Client({ connectionString: req("SUPABASE_DB_URL"), ssl:{rejectUnauthorized:false}, connectionTimeoutMillis:20000 });
  await c.connect();
  const tables = ["bank","unit","question","practice_record","appeal","ai_config","ai_call_log","profiles","import_task"];
  for (const t of tables) {
    const r = await c.query("select column_name, data_type from information_schema.columns where table_schema='public' and table_name=$1 order by ordinal_position", [t]);
    console.log("== " + t + " == " + (r.rows.length ? r.rows.map(x=>x.column_name+":"+x.data_type).join(", ") : "MISSING"));
  }
  await c.end();
})().catch(e=>{console.error("ERR", e.message); process.exit(1);});
