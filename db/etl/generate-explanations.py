#!/usr/bin/env python3
"""为公共题库批量生成 AI 解析，产出 Flyway 迁移 SQL。

为什么要有这个脚本：公共题库（软考真题 322 道）的 explanation 是空的，
而 SEO 的题目详情页只有「题干 + 选项 + 答案」，对搜索引擎来说是薄页面。
把解析补齐，这些页面才真正有内容。

用法：
    python db/etl/generate-explanations.py --limit 5    # 先跑 5 道看效果
    python db/etl/generate-explanations.py              # 全量（约 322 次调用，成本约 ¥1）

前置：
    - 本机 MySQL 里有 shuati 库（db/mysql/*.sql 建的那个），且已导入 soft_exam.sql
    - 仓库根目录 .env.local 里有 DEEPSEEK_API_KEY / DEEPSEEK_BASE_URL / DEEPSEEK_MODEL
    - 环境变量 SHUATI_DB_PASSWORD 是本机 MySQL 口令

产物：backend/src/main/resources/db/migration/V14__public_bank_explanations.sql
      迁移按内容 MD5 定位（不依赖自增 id），且只填 explanation 为空的题。
"""

import argparse
import hashlib
import json
import os
import subprocess
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from urllib import error, request

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ENV_FILE = os.path.join(ROOT, ".env.local")
OUT_FILE = os.path.join(
    ROOT, "backend", "src", "main", "resources", "db", "migration",
    "V14__public_bank_explanations.sql",
)
MYSQL_BIN = os.environ.get(
    "MYSQL_BIN", r"C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe"
)

# 与 backend 的 PromptTemplates.EXPLAIN_SYSTEM 保持一致，改一处要同步另一处，
# 否则 App 里点「AI 解析」出来的风格和这里生成的不一样。
CHOICE_SYSTEM = """你是 Java 面试讲师。根据题目、选项和正确答案，输出一段 Markdown 解析。
要求：
1. 开篇点明正确答案，并说明它为什么正确；
2. 逐个选项简述为什么对/为什么错；
3. 适当补充面试考点延伸；
4. 解析只针对题目本身，禁止提及任何考生的作答、选择或对错；
5. 不要用 LaTeX / 数学公式语法，符号用纯文本（例如 P1→P2、O(n)）；
6. 300 字以内，直接输出 Markdown，不要输出 JSON。"""

# 简答题没有选项，把「逐选项」「点明正确答案」换成讲清参考要点
SHORT_SYSTEM = """你是资深技术讲师。根据题目、评分要点和参考答案，输出一段 Markdown 解析。
要求：
1. 开篇直接给出要点式的参考答案；
2. 逐条说明每个得分点为什么重要；
3. 适当补充考点延伸；
4. 解析只针对题目本身，禁止提及任何考生的作答；
5. 不要用 LaTeX / 数学公式语法，符号用纯文本；
6. 300 字以内，直接输出 Markdown，不要输出 JSON。"""


def load_env():
    env = {}
    if not os.path.exists(ENV_FILE):
        return env
    with open(ENV_FILE, "r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            env[key.strip()] = value.strip().strip("'\"")
    return env


def fetch_questions(password, limit):
    # 用 json_object 输出，每行一个 JSON，避免题干里的换行把 TSV 拆散
    sql = """
select json_object(
         'unit', u.name,
         'type', q.type,
         'content', q.content,
         'options', coalesce(q.options, cast('[]' as json)),
         'keyPoints', coalesce(q.key_points, cast('[]' as json)),
         'answer', q.answer,
         'hash', md5(q.content)
       )
  from question q
  join unit u on u.id = q.unit_id
  join bank b on b.id = u.bank_id
 where b.owner_id is null and q.status = 'ON'
   and (q.explanation is null or q.explanation = '')
 order by q.id
"""
    if limit:
        sql += f" limit {int(limit)}\n"
    result = subprocess.run(
        [MYSQL_BIN, "-u", "root", f"--password={password}", "--default-character-set=utf8mb4",
         "-N", "-B", "--raw", "shuati", "-e", sql],
        capture_output=True, text=True, encoding="utf-8",
    )
    if result.returncode != 0:
        sys.exit("读取题库失败：\n" + (result.stderr or ""))
    questions = []
    for line in result.stdout.splitlines():
        line = line.strip()
        if line:
            questions.append(json.loads(line))
    return questions


def option_lines(raw_json):
    try:
        options = json.loads(raw_json) if isinstance(raw_json, str) else raw_json
    except json.JSONDecodeError:
        return []
    return [f"{item.get('key')}. {item.get('text')}" for item in options or []]


def build_prompt(question):
    content = question["content"]
    if question["type"] == "SHORT":
        points = question.get("keyPoints") or []
        if isinstance(points, str):
            points = json.loads(points)
        lines = "\n".join(f"- {point}" for point in points)
        user = f"【题目】\n{content}\n\n【评分要点】\n{lines}\n\n【参考答案】\n{question['answer']}"
        return SHORT_SYSTEM, user
    options = "\n".join(option_lines(question["options"]))
    user = f"【题目】\n{content}\n\n【选项】\n{options}\n\n【正确答案】{question['answer']}"
    return CHOICE_SYSTEM, user


def explain(question, env, retries=3):
    system, user = build_prompt(question)
    body = json.dumps({
        "model": env["DEEPSEEK_MODEL"],
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ],
        "temperature": 0.3,
        "stream": False,
    }).encode("utf-8")
    last = None
    for attempt in range(retries):
        try:
            req = request.Request(
                env["DEEPSEEK_BASE_URL"].rstrip("/") + "/chat/completions",
                data=body,
                headers={
                    "Authorization": "Bearer " + env["DEEPSEEK_API_KEY"],
                    "Content-Type": "application/json",
                },
            )
            with request.urlopen(req, timeout=180) as response:
                payload = json.loads(response.read().decode("utf-8"))
            return payload["choices"][0]["message"]["content"].strip()
        except (error.HTTPError, error.URLError, KeyError, json.JSONDecodeError) as exc:
            last = exc
            time.sleep(2 * (attempt + 1))
    raise RuntimeError(f"调用失败：{last}")


def sql_literal(text):
    return "'" + text.replace("\\", "\\\\").replace("'", "''") + "'"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--limit", type=int, default=0, help="只处理前 N 道，用于试跑")
    parser.add_argument("--workers", type=int, default=4, help="并发数")
    args = parser.parse_args()

    env = load_env()
    for key in ("DEEPSEEK_API_KEY", "DEEPSEEK_BASE_URL", "DEEPSEEK_MODEL"):
        if not env.get(key):
            sys.exit(f".env.local 缺少 {key}")
    password = os.environ.get("SHUATI_DB_PASSWORD")
    if not password:
        sys.exit("请先设置环境变量 SHUATI_DB_PASSWORD")

    questions = fetch_questions(password, args.limit)
    if not questions:
        sys.exit("没有需要补解析的题目")

    # MD5 是跨库定位题目的依据，先确认 MySQL 算的和 Python 算的一致
    for question in questions[:3]:
        local = hashlib.md5(question["content"].encode("utf-8")).hexdigest()
        if local != question["hash"]:
            sys.exit(f"MD5 不一致，脚本的定位方式不可靠：{local} != {question['hash']}")

    print(f"待生成 {len(questions)} 道，模型 {env['DEEPSEEK_MODEL']}，并发 {args.workers}")
    results = []
    started = time.time()
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = {pool.submit(explain, question, env): question for question in questions}
        for index, future in enumerate(futures, start=1):
            question = futures[future]
            try:
                text = future.result()
                results.append((question, text))
                print(f"[{index}/{len(questions)}] ok  {question['unit']} :: "
                      f"{question['content'][:24]}…")
            except Exception as exc:  # 单条失败不影响整体
                print(f"[{index}/{len(questions)}] 失败：{exc}")

    if not results:
        sys.exit("全部失败，未生成文件")

    lines = [
        "-- 公共题库的 AI 解析，补齐题目详情页的内容深度。",
        f"-- 由 db/etl/generate-explanations.py 生成，模型 {env['DEEPSEEK_MODEL']}，"
        f"共 {len(results)} 道。",
        "-- 按内容 MD5 定位，避免依赖自增 id；只填空解析，已有解析的题目不动。",
        "",
    ]
    for question, text in results:
        lines.append(
            f"update question set explanation = {sql_literal(text)} "
            f"where md5(content) = '{question['hash']}' "
            f"and (explanation is null or explanation = '');"
        )
    with open(OUT_FILE, "w", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(lines) + "\n")

    print(f"\n已写入 {OUT_FILE}")
    print(f"成功 {len(results)}/{len(questions)} 道，用时 {time.time() - started:.0f} 秒")


if __name__ == "__main__":
    main()
