const fs = require("fs");
const path = require("path");

// Minimal .env loader so local helper scripts work without extra dependencies.
function loadEnvFile(file) {
  if (!fs.existsSync(file)) return;
  for (const rawLine of fs.readFileSync(file, "utf8").split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#")) continue;
    const eq = line.indexOf("=");
    if (eq === -1) continue;
    const key = line.slice(0, eq).trim();
    let value = line.slice(eq + 1).trim();
    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1);
    }
    if (!(key in process.env)) process.env[key] = value;
  }
}

loadEnvFile(path.join(__dirname, "..", ".env.local"));

function req(name) {
  const value = process.env[name];
  if (!value) {
    throw new Error(
      "Missing required environment variable " + name + " (set it in .env.local or your shell)",
    );
  }
  return value;
}

module.exports = { req };
