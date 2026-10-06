import { runImportTask } from "../../lib/import";

export default async function handler(event: { body?: string | null }) {
  try {
    const body = JSON.parse(event.body || "{}");
    if (!body.taskId) {
      return { statusCode: 400, body: "missing taskId" };
    }
    await runImportTask(body.taskId);
    return { statusCode: 202, body: "ok" };
  } catch (e) {
    return { statusCode: 500, body: String(e) };
  }
}