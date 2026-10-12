import { expect, test } from "@playwright/test";

test.use({
  viewport: { width: 390, height: 844 },
  isMobile: true,
  hasTouch: true,
});

test("mobile app shell uses bottom navigation without horizontal overflow", async ({ page }) => {
  await page.route("**/api/me", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: {
          id: "test-user",
          email: "shuati.admin@gmail.com",
          nickname: "admin",
          role: "ADMIN",
          status: "ENABLED",
        },
      },
    }),
  );
  await page.route("**/api/points", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: {
          entitlements: {
            planCode: "free",
            planName: "免费版",
            planExpiresAt: null,
            monthlyQuota: 50,
            monthlyUsed: 0,
            monthlyLeft: 50,
            bonusBalance: 100,
            available: 150,
            lifetimeUsed: 0,
          },
          ledger: [],
        },
      },
    }),
  );
  await page.route("**/api/banks", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: [],
      },
    }),
  );

  await page.goto("/app");

  await expect(page.getByTestId("mobile-nav")).toBeVisible();
  await expect(page.getByTestId("desktop-sidebar")).toBeHidden();

  const hasHorizontalOverflow = await page.evaluate(
    () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
  );
  expect(hasHorizontalOverflow).toBe(false);
});

test("site favicon uses the Shuati leaf brand mark", async ({ page, request }) => {
  await page.goto("/login");

  const icon = page.locator('link[rel="icon"]');
  await expect(icon).toHaveAttribute("href", "/favicon.svg");

  const response = await request.get("/favicon.svg");
  expect(response.ok()).toBe(true);
  expect(await response.text()).toContain("<svg");
});

test("captcha is only required for login, not register or forgot password", async ({ page }) => {
  await page.route("**/captcha/image", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: { ticket: "t1", imageBase64: "data:image/png;base64,AA==" },
      },
    }),
  );

  await page.goto("/login");
  const captchaInput = page.getByPlaceholder("请输入图中字符");
  await expect(captchaInput).toBeVisible();

  await page.getByRole("button", { name: "注册", exact: true }).click();
  await expect(captchaInput).toBeHidden();

  await page.getByRole("button", { name: "找回密码", exact: true }).click();
  await expect(captchaInput).toBeHidden();

  await page.getByRole("button", { name: "登录", exact: true }).click();
  await expect(captchaInput).toBeVisible();
});

async function mockGuestBankList(page: import("@playwright/test").Page) {
  await page.route("**/api/me", (route) =>
    route.fulfill({ status: 401, json: { code: 401, message: "请先登录", data: null } }),
  );
  await page.route("**/api/banks", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: [
          {
            id: 5,
            name: "软考真题",
            description: null,
            is_default: false,
            is_public: true,
            unit_count: 1,
            question_count: 12,
            answered_count: 0,
          },
        ],
      },
    }),
  );
  await page.route("**/api/banks/5/units", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: [
          {
            id: 9,
            bank_id: 5,
            name: "操作系统基础",
            sort: 0,
            question_count: 12,
            answered_count: 0,
          },
        ],
      },
    }),
  );
}

test("guest can browse public banks without being sent to login", async ({ page }) => {
  await mockGuestBankList(page);

  await page.goto("/app");

  await expect(page).toHaveURL(/\/app$/);
  await expect(page.getByText(/游客模式：公共题库可直接浏览/)).toBeVisible();
  await expect(page.getByRole("button", { name: /软考真题/ })).toBeVisible();
  await expect(page.getByRole("button", { name: "登录后开始练习" })).toBeVisible();
});

test("guest is asked to log in before practicing", async ({ page }) => {
  await mockGuestBankList(page);

  await page.goto("/app");
  await page.getByRole("button", { name: "登录后开始练习" }).click();

  await expect(page).toHaveURL(/\/login\?next=/);
});

test("registration sends email code and starts sixty second countdown", async ({ page }) => {
  await page.route("**/captcha/image", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: { ticket: "t1", imageBase64: "data:image/png;base64,AA==" },
      },
    }),
  );
  await page.route("**/api/auth/register/email-code", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: { cooldownSeconds: 60, expiresInSeconds: 300 },
      },
    }),
  );

  await page.goto("/login");
  await page.getByRole("button", { name: "注册", exact: true }).click();
  await page.getByRole("textbox", { name: "邮箱", exact: true }).fill("user@example.com");
  await page.getByRole("button", { name: "发送验证码" }).click();

  await expect(page.getByRole("button", { name: /60 秒后重发/ })).toBeDisabled();
  await expect(page.getByPlaceholder("6 位数字")).toBeVisible();
});

test("changing email clears old code and countdown", async ({ page }) => {
  await page.route("**/captcha/image", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: { ticket: "t1", imageBase64: "data:image/png;base64,AA==" },
      },
    }),
  );
  await page.route("**/api/auth/register/email-code", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: { cooldownSeconds: 60, expiresInSeconds: 300 },
      },
    }),
  );

  await page.goto("/login");
  await page.getByRole("button", { name: "注册", exact: true }).click();
  await page.getByRole("textbox", { name: "邮箱", exact: true }).fill("first@example.com");
  await page.getByRole("button", { name: "发送验证码" }).click();
  await page.getByRole("textbox", { name: "邮箱", exact: true }).fill("second@example.com");

  await expect(page.getByPlaceholder("6 位数字")).toHaveValue("");
  await expect(page.getByRole("button", { name: "发送验证码" })).toBeEnabled();
});

test("rate limited send resumes countdown from server retry time", async ({ page }) => {
  await page.route("**/captcha/image", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: { ticket: "t1", imageBase64: "data:image/png;base64,AA==" },
      },
    }),
  );
  await page.route("**/api/auth/register/email-code", (route) =>
    route.fulfill({
      status: 429,
      headers: { "Retry-After": "42" },
      json: { code: 429, message: "验证码发送过于频繁，请 42 秒后重试", data: null },
    }),
  );

  await page.goto("/login");
  await page.getByRole("button", { name: "注册", exact: true }).click();
  await page.getByRole("textbox", { name: "邮箱", exact: true }).fill("limited@example.com");
  await page.getByRole("button", { name: "发送验证码" }).click();

  await expect(page.getByRole("button", { name: /42 秒后重发/ })).toBeDisabled();
});

test("forgot password sends reset code and resets password", async ({ page }) => {
  let resetPayload: Record<string, string> | null = null;

  await page.route("**/captcha/image", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: { ticket: "t1", imageBase64: "data:image/png;base64,AA==" },
      },
    }),
  );
  await page.route("**/api/auth/password-reset/email-code", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: { cooldownSeconds: 60, expiresInSeconds: 300 },
      },
    }),
  );
  await page.route("**/api/auth/password-reset", async (route) => {
    resetPayload = route.request().postDataJSON();
    await route.fulfill({ json: { code: 200, message: "ok", data: null } });
  });

  await page.goto("/login");
  await page.getByRole("button", { name: "找回密码", exact: true }).click();
  await page.getByRole("textbox", { name: "邮箱", exact: true }).fill("reset@example.com");
  await page.getByRole("button", { name: "发送验证码" }).click();
  await page.getByPlaceholder("6 位数字").fill("123456");
  await page.getByRole("textbox", { name: "新密码", exact: true }).fill("new-secret");
  await page.getByRole("textbox", { name: "确认新密码", exact: true }).fill("new-secret");
  await page.getByRole("button", { name: "重置密码" }).click();

  await expect(page).toHaveURL(/\/login/);
  await expect(page.getByText("密码已重置，请使用新密码登录")).toBeVisible();
  expect(resetPayload).toEqual({
    email: "reset@example.com",
    emailCode: "123456",
    newPassword: "new-secret",
  });
});

test("stats table scrolls horizontally on mobile instead of being clipped", async ({ page }) => {
  await page.route("**/api/me", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: {
          id: "test-user",
          email: "shuati.admin@gmail.com",
          nickname: "admin",
          role: "ADMIN",
          status: "ENABLED",
        },
      },
    }),
  );
  await page.route("**/api/points", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: {
          entitlements: {
            planCode: "free",
            planName: "免费版",
            planExpiresAt: null,
            monthlyQuota: 50,
            monthlyUsed: 0,
            monthlyLeft: 50,
            bonusBalance: 100,
            available: 150,
            lifetimeUsed: 0,
          },
          ledger: [],
        },
      },
    }),
  );
  await page.route("**/api/stats/me", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: {
          overall: { total: 1, correct: 1, partial: 0, wrong: 0, pending: 0 },
          byUnit: [{ bank_name: "测试题库", unit_name: "第一章", total: 1, correct: 1 }],
        },
      },
    }),
  );
  await page.route("**/api/practice/sessions", (route) =>
    route.fulfill({ json: { code: 200, message: "ok", data: [] } }),
  );

  await page.goto("/app/stats");
  const table = page.locator("table").first();
  await expect(table).toBeVisible();

  const overflowX = await table.evaluate(
    (element) => getComputedStyle(element.parentElement as HTMLElement).overflowX,
  );
  expect(overflowX).toBe("auto");
});

test("practice actions stay directly above the mobile navigation", async ({ page }) => {
  await page.route("**/api/me", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: {
          id: "test-user",
          email: "shuati.admin@gmail.com",
          nickname: "admin",
          role: "ADMIN",
          status: "ENABLED",
        },
      },
    }),
  );
  await page.route("**/api/points", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: {
          entitlements: {
            planCode: "free",
            planName: "免费版",
            planExpiresAt: null,
            monthlyQuota: 50,
            monthlyUsed: 0,
            monthlyLeft: 50,
            bonusBalance: 100,
            available: 150,
            lifetimeUsed: 0,
          },
          ledger: [],
        },
      },
    }),
  );
  await page.route("**/api/units/1/questions*", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: [
          {
            id: 1,
            unit_id: 1,
            type: "SINGLE",
            content: "测试题干",
            options: [{ key: "A", text: "选项 A" }],
            difficulty: "MEDIUM",
            images: null,
            tags: null,
            status: "ON",
          },
        ],
      },
    }),
  );
  await page.route("**/api/practice/sessions", (route) =>
    route.fulfill({ json: { code: 200, message: "ok", data: { id: 1 } } }),
  );

  await page.goto("/app/practice?unitId=1");
  const actions = page.getByTestId("practice-actions");
  await expect(actions).toBeVisible();

  const position = await actions.evaluate((element) => getComputedStyle(element).position);
  expect(position).toBe("fixed");
});

test("practice resumes from the first unanswered question", async ({ page }) => {
  const question = (id: number) => ({
    id,
    unit_id: 1,
    type: "SINGLE",
    content: `第 ${id} 题题干`,
    options: [{ key: "A", text: "选项 A" }],
    difficulty: "MEDIUM",
    images: null,
    tags: null,
    status: "ON",
  });

  await page.route("**/api/me", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: { id: "test-user", email: "u@example.com", nickname: "u", role: "USER", status: "ENABLED" },
      },
    }),
  );
  await page.route("**/api/points", (route) =>
    route.fulfill({ json: { code: 200, message: "ok", data: { entitlements: {}, ledger: [] } } }),
  );
  await page.route("**/api/units/1/questions*", (route) =>
    route.fulfill({
      json: { code: 200, message: "ok", data: [question(1), question(2), question(3)] },
    }),
  );
  await page.route("**/api/practice/sessions", (route) =>
    route.fulfill({ json: { code: 200, message: "ok", data: { id: 1 } } }),
  );
  // 前两题已经作答过，续刷应该落到第 3 题
  await page.route("**/api/progress/unit/1", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: { total: 3, answered: 2, correct: 1, answeredIds: [1, 2] },
      },
    }),
  );

  await page.goto("/app/practice?unitId=1");

  await expect(page.getByText("第 3 / 3 题")).toBeVisible();
  await expect(page.getByText("已从上次进度继续")).toBeVisible();
});

test("stored explanation shows after answering and can be regenerated", async ({ page }) => {
  await page.route("**/api/me", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: { id: "test-user", email: "u@example.com", nickname: "u", role: "USER", status: "ENABLED" },
      },
    }),
  );
  await page.route("**/api/points", (route) =>
    route.fulfill({ json: { code: 200, message: "ok", data: { entitlements: {}, ledger: [] } } }),
  );
  await page.route("**/api/units/1/questions*", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: [
          {
            id: 1,
            unit_id: 1,
            type: "SINGLE",
            content: "解析库测试题干",
            options: [{ key: "A", text: "选项 A" }],
            difficulty: "MEDIUM",
            images: null,
            tags: null,
            status: "ON",
            explanation: "解析库里的解析内容",
          },
        ],
      },
    }),
  );
  await page.route("**/api/practice/sessions", (route) =>
    route.fulfill({ json: { code: 200, message: "ok", data: { id: 1 } } }),
  );
  await page.route("**/api/progress/unit/1", (route) =>
    route.fulfill({
      json: { code: 200, message: "ok", data: { total: 1, answered: 0, correct: 0, answeredIds: [] } },
    }),
  );
  await page.route("**/api/practice/submit-choice", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: { recordId: 1, verdict: "CORRECT", score: 10, correctAnswer: "A" },
      },
    }),
  );
  await page.route("**/api/practice/questions/1/explanation*", (route) =>
    route.fulfill({
      json: { code: 200, message: "ok", data: { explanation: "重新生成的解析内容" } },
    }),
  );

  await page.goto("/app/practice?unitId=1");

  // 未作答前不应该剧透解析（解析里含正确答案）
  await expect(page.getByText("解析库里的解析内容")).toBeHidden();

  await page.getByRole("button", { name: /选项 A/ }).click();
  await page.getByRole("button", { name: "提交答案" }).click();

  // 作答后直接带出解析库内容，无需点 AI 解析
  await expect(page.getByText("来自解析库")).toBeVisible();
  await expect(page.getByText("解析库里的解析内容")).toBeVisible();

  // 再次点击＝重新生成并覆盖展示
  await page.getByRole("button", { name: "重新生成解析" }).click();
  await expect(page.getByText("本次 AI 解析")).toBeVisible();
  await expect(page.getByText("重新生成的解析内容")).toBeVisible();
});

test("clicking anywhere on a point pack card starts checkout", async ({ page }) => {
  let orderPayload: Record<string, unknown> | null = null;

  await page.route("**/api/me", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: { id: "u", email: "u@example.com", nickname: "u", role: "USER", status: "ENABLED" },
      },
    }),
  );
  await page.route("**/api/plans", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: {
          plans: [],
          packs: [
            {
              code: "pack_9",
              name: "轻量加量包",
              price_cents: 900,
              points: 400,
              bonus_points: 0,
              currency: "CNY",
            },
          ],
          rules: [],
        },
      },
    }),
  );
  await page.route("**/api/points", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: {
          entitlements: {
            planCode: "free",
            planName: "免费版",
            monthlyQuota: 50,
            monthlyLeft: 50,
            bonusBalance: 100,
            available: 150,
          },
          ledger: [],
        },
      },
    }),
  );
  await page.route("**/api/orders", async (route) => {
    if (route.request().method() === "GET") {
      await route.fulfill({ json: { code: 200, message: "ok", data: [] } });
      return;
    }
    orderPayload = route.request().postDataJSON();
    await route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: {
          id: "o1",
          kind: "PACK",
          item_code: "pack_9",
          amount_cents: 900,
          points: 400,
          checkoutUrl: "https://checkout.example.com/s/abc",
        },
      },
    });
  });
  // 收银台会在新标签页打开，必须用 context 级路由才能拦到弹窗的请求
  await page.context().route("https://checkout.example.com/**", (route) =>
    route.fulfill({ status: 200, contentType: "text/html", body: "<html>checkout stub</html>" }),
  );

  await page.goto("/app/pricing");

  const popupPromise = page.waitForEvent("popup");
  // 点卡片正文（不是金额徽章）也应能触发下单
  await page.getByRole("button", { name: /轻量加量包/ }).click();
  const popup = await popupPromise;

  await expect(page.getByText("已打开收银台，完成支付后会自动开通。")).toBeVisible();
  expect(orderPayload).toMatchObject({ kind: "PACK", itemCode: "pack_9" });
  expect(popup.url()).toContain("checkout.example.com");
});

test("import is blocked while another task is still running", async ({ page }) => {
  await page.route("**/api/me", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: { id: "u", email: "u@example.com", nickname: "u", role: "USER", status: "ENABLED" },
      },
    }),
  );
  await page.route("**/api/points", (route) =>
    route.fulfill({ json: { code: 200, message: "ok", data: { entitlements: {}, ledger: [] } } }),
  );
  await page.route("**/api/import/list", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: [
          {
            id: "t-running",
            kind: "text",
            phase: "PARSE",
            status: "RUNNING",
            progress: 40,
            total_chunks: 5,
            done_chunks: 2,
            bank_name: "正在解析的题库",
            error: null,
          },
        ],
      },
    }),
  );

  await page.goto("/app/import");

  const button = page.getByRole("button", { name: "有任务解析中…" });
  await expect(button).toBeDisabled();
  await expect(page.getByText("同一时间只解析一个任务")).toBeVisible();
});

test("parse result supports select all / none / invert", async ({ page }) => {
  const task = {
    id: "t1",
    kind: "text",
    phase: "READY",
    status: "COMPLETED",
    progress: 100,
    total_chunks: 1,
    done_chunks: 1,
    bank_name: "产品",
    error: null,
    items: [
      { unit: "文档概述", type: "SINGLE", content: "第一题", answer: "A" },
      { unit: "文档概述", type: "SINGLE", content: "第二题", answer: "B" },
      { unit: "文档概述", type: "MULTI", content: "第三题", answer: "CD" },
    ],
  };

  await page.route("**/api/me", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: { id: "u", email: "u@example.com", nickname: "u", role: "USER", status: "ENABLED" },
      },
    }),
  );
  await page.route("**/api/points", (route) =>
    route.fulfill({ json: { code: 200, message: "ok", data: { entitlements: {}, ledger: [] } } }),
  );
  await page.route("**/api/import/list", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: [{ ...task, items: undefined }],
      },
    }),
  );
  await page.route("**/api/import/task/t1", (route) =>
    route.fulfill({ json: { code: 200, message: "ok", data: task } }),
  );

  await page.goto("/app/import");
  await page.getByRole("button", { name: /产品/ }).click();

  // 打开任务默认全选
  await expect(page.getByText("3 / 3 道题")).toBeVisible();
  const importButton = page.getByRole("button", { name: /导入选中的/ });
  await expect(importButton).toBeEnabled();

  await page.getByRole("button", { name: "全不选" }).click();
  await expect(page.getByText("0 / 3 道题")).toBeVisible();
  await expect(importButton).toBeDisabled();

  await page.getByRole("button", { name: "全选" }).click();
  await expect(page.getByText("3 / 3 道题")).toBeVisible();

  await page.getByRole("button", { name: "反选" }).click();
  await expect(page.getByText("0 / 3 道题")).toBeVisible();

  // 勾一题再反选 => 剩两题
  await page.getByRole("checkbox").first().check();
  await page.getByRole("button", { name: "反选" }).click();
  await expect(page.getByText("2 / 3 道题")).toBeVisible();
});

async function mockImportReadyTask(
  page: import("@playwright/test").Page,
  role: "USER" | "ADMIN",
  onImport: (payload: Record<string, unknown> | null) => void,
) {
  await page.route("**/api/me", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: { id: "u", email: "u@example.com", nickname: "u", role, status: "ENABLED" },
      },
    }),
  );
  await page.route("**/api/points", (route) =>
    route.fulfill({ json: { code: 200, message: "ok", data: { entitlements: {}, ledger: [] } } }),
  );
  // 普通用户拿到的列表只含自己的题库 + 公共题库
  await page.route("**/api/banks", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: [
          { id: 11, name: "我的题库", is_public: false, owner_id: "u" },
          { id: 12, name: "软考真题", is_public: true, owner_id: null },
        ],
      },
    }),
  );
  const task = {
    id: "t1",
    kind: "text",
    phase: "READY",
    status: "COMPLETED",
    progress: 100,
    total_chunks: 1,
    done_chunks: 1,
    bank_name: "",
    error: null,
  };
  await page.route("**/api/import/list", (route) =>
    route.fulfill({ json: { code: 200, message: "ok", data: [task] } }),
  );
  await page.route("**/api/import/task/t1", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: {
          ...task,
          items: [{ unit: "文档概述", type: "SINGLE", content: "第一题", answer: "A" }],
        },
      },
    }),
  );
  await page.route("**/api/import/task/t1/import", async (route) => {
    onImport(route.request().postDataJSON());
    await route.fulfill({ json: { code: 200, message: "ok", data: null } });
  });
}

test("import can target an existing bank, public bank hidden from normal users", async ({ page }) => {
  let payload: Record<string, unknown> | null = null;
  await mockImportReadyTask(page, "USER", (p) => {
    payload = p;
  });

  await page.goto("/app/import");
  await page.getByRole("button", { name: /未命名任务/ }).click();

  // 任务没填题库名，默认落到「导入到已有题库」
  const options = page.locator("select option");
  await expect(options).toContainText(["请选择题库…", "我的题库"]);
  await expect(page.getByText("软考真题")).toHaveCount(0);

  await page.getByRole("button", { name: /导入选中的/ }).click();
  expect(payload).toMatchObject({ targetBankId: 11 });
});

test("admin can also import into a public bank", async ({ page }) => {
  let payload: Record<string, unknown> | null = null;
  await mockImportReadyTask(page, "ADMIN", (p) => {
    payload = p;
  });

  await page.goto("/app/import");
  await page.getByRole("button", { name: /未命名任务/ }).click();

  await page.locator("select").selectOption({ label: "软考真题（公共题库）" });
  await page.getByRole("button", { name: /导入选中的/ }).click();
  expect(payload).toMatchObject({ targetBankId: 12 });
});

async function mockBankList(page: import("@playwright/test").Page, role: "USER" | "ADMIN") {
  await page.route("**/api/me", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: { id: "u", email: "u@example.com", nickname: "u", role, status: "ENABLED" },
      },
    }),
  );
  await page.route("**/api/points", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: {
          entitlements: { planCode: "free", planName: "免费版", monthlyQuota: 50, monthlyLeft: 50, bonusBalance: 100, available: 100 },
          ledger: [],
        },
      },
    }),
  );
  await page.route("**/api/banks", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: [
          { id: 11, name: "我的题库", description: null, is_default: false, is_public: false, owner_id: "u", unit_count: 0, question_count: 0, answered_count: 0 },
          { id: 12, name: "软考真题", description: null, is_default: false, is_public: true, owner_id: null, unit_count: 0, question_count: 0, answered_count: 0 },
        ],
      },
    }),
  );
  await page.route("**/api/banks/11/units", (route) =>
    route.fulfill({ json: { code: 200, message: "ok", data: [] } }),
  );
  await page.route("**/api/banks/12/units", (route) =>
    route.fulfill({ json: { code: 200, message: "ok", data: [] } }),
  );
}

test("bank maintenance entry only shows for banks you may maintain", async ({ page }) => {
  await mockBankList(page, "USER");
  await page.goto("/app");

  // 默认选中第一个题库（自己的），有维护入口
  await expect(page.getByRole("link", { name: "维护" })).toBeVisible();

  // 切到公共题库：普通用户看不到维护入口
  await page.getByRole("button", { name: /软考真题/ }).click();
  await expect(page.getByRole("link", { name: "维护" })).toBeHidden();
});

test("admin sees the maintenance entry on a public bank too", async ({ page }) => {
  await mockBankList(page, "ADMIN");
  await page.goto("/app");
  await page.getByRole("button", { name: /软考真题/ }).click();
  await expect(page.getByRole("link", { name: "维护" })).toBeVisible();
});

test("bank maintenance page can add and rename units", async ({ page }) => {
  await mockBankList(page, "USER");
  let unitPayload: Record<string, unknown> | null = null;
  let renames = 0;

  await page.route("**/api/manage/banks", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: [{ id: 11, name: "我的题库", description: "备注", is_public: false }],
      },
    }),
  );
  await page.route("**/api/manage/banks/11/units", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: [{ id: 21, bank_id: 11, name: "第一章", sort: 0, question_count: 3, off_count: 0 }],
      },
    }),
  );
  await page.route("**/api/manage/units", (route) => {
    unitPayload = route.request().postDataJSON();
    return route.fulfill({ json: { code: 200, message: "ok", data: { id: 22 } } });
  });
  await page.route("**/api/manage/units/21", (route) => {
    renames += 1;
    return route.fulfill({ json: { code: 200, message: "ok", data: null } });
  });
  await page.route("**/api/manage/banks/11/questions*", (route) =>
    route.fulfill({ json: { code: 200, message: "ok", data: [] } }),
  );

  await page.goto("/app/banks/11");

  await expect(page.getByRole("heading", { name: "题库管理" })).toBeVisible();
  await expect(page.getByText("第一章")).toBeVisible();
  await expect(page.getByText("3 题")).toBeVisible();

  // 新建单元
  await page.getByPlaceholder("新单元名称").fill("第二章");
  await page.getByRole("button", { name: "新建单元" }).click();
  expect(unitPayload).toMatchObject({ bankId: 11, name: "第二章" });

  // 重命名单元
  await page.getByTitle("重命名").click();
  await page.getByLabel("单元名称").fill("第一章（改）");
  await page.getByTitle("保存").click();
  expect(renames).toBe(1);
});

test("import page locks onto the bank it was opened from", async ({ page }) => {
  let payload: Record<string, unknown> | null = null;
  await mockImportReadyTask(page, "USER", (p) => {
    payload = p;
  });

  // 从题库管理页点「导入题目」进来时带着 bankId，导入去向直接锁死
  await page.goto("/app/import?bankId=11");
  await expect(page.getByText("导入到：")).toBeVisible();
  await expect(page.locator("select")).toHaveCount(0);

  await page.getByRole("button", { name: /未命名任务/ }).click();
  await page.getByRole("button", { name: /导入选中的/ }).click();
  expect(payload).toMatchObject({ targetBankId: 11 });
});

test("bank management page imports into the bank and adds questions by hand", async ({ page }) => {
  await mockBankList(page, "USER");
  let created: Record<string, unknown> | null = null;

  await page.route("**/api/manage/banks", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: [{ id: 11, name: "我的题库", description: null, is_public: false }],
      },
    }),
  );
  await page.route("**/api/manage/banks/11/units", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: [{ id: 21, bank_id: 11, name: "第一章", sort: 0, question_count: 0, off_count: 0 }],
      },
    }),
  );
  await page.route("**/api/manage/banks/11/questions*", (route) =>
    route.fulfill({ json: { code: 200, message: "ok", data: [] } }),
  );
  await page.route("**/api/manage/questions", (route) => {
    created = route.request().postDataJSON();
    return route.fulfill({ json: { code: 200, message: "ok", data: { id: 99 } } });
  });

  await page.goto("/app/banks/11");
  await expect(page.getByRole("heading", { name: "题库管理" })).toBeVisible();

  // 导入题目留在题库管理里，跳转时把目标题库一起带上
  await expect(page.getByRole("link", { name: "导入题目" })).toHaveAttribute(
    "href",
    "/app/import?bankId=11",
  );

  // 手工新增一道单选题：填空、点选项字母设成答案、保存
  await page.getByRole("button", { name: "新增题目" }).click();
  await page.getByLabel("所属单元").selectOption({ label: "第一章" });
  await page.getByPlaceholder("支持 Markdown 语法").fill("下面哪个是索引的作用？");
  await page.getByPlaceholder("选项 A 内容").fill("加快查询");
  await page.getByPlaceholder("选项 B 内容").fill("加快写入");
  await page.getByTitle("把 A 设为正确答案").click();
  await page.getByRole("button", { name: "保存题目" }).click();

  expect(created).toMatchObject({
    bankId: 11,
    unitId: 21,
    type: "SINGLE",
    content: "下面哪个是索引的作用？",
    answer: "A",
    options: [
      { key: "A", text: "加快查询" },
      { key: "B", text: "加快写入" },
    ],
  });
});

test("questions without a unit go to the default unit", async ({ page }) => {
  await mockBankList(page, "USER");
  let created: Record<string, unknown> | null = null;

  await page.route("**/api/manage/banks", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: [{ id: 11, name: "我的题库", description: null, is_public: false }],
      },
    }),
  );
  await page.route("**/api/manage/banks/11/units", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: [{ id: 21, bank_id: 11, name: "第一章", sort: 0, question_count: 0, off_count: 0 }],
      },
    }),
  );
  await page.route("**/api/manage/banks/11/questions*", (route) =>
    route.fulfill({ json: { code: 200, message: "ok", data: [] } }),
  );
  await page.route("**/api/manage/questions", (route) => {
    created = route.request().postDataJSON();
    return route.fulfill({ json: { code: 200, message: "ok", data: { id: 99 } } });
  });

  await page.goto("/app/banks/11");
  await page.getByRole("button", { name: "新增题目" }).click();

  // 单元选填：默认停在「默认单元（未分类）」，直接保存也能过
  await expect(page.getByLabel("所属单元").locator("option:checked")).toHaveText(
    "默认单元（未分类）",
  );
  await page.getByPlaceholder("支持 Markdown 语法").fill("不选单元的题目");
  await page.getByPlaceholder("选项 A 内容").fill("甲");
  await page.getByPlaceholder("选项 B 内容").fill("乙");
  await page.getByTitle("把 B 设为正确答案").click();
  await page.getByRole("button", { name: "保存题目" }).click();

  expect(created).toMatchObject({ bankId: 11, unitId: null, answer: "B" });
});

test("sidebar no longer carries the standalone import entry", async ({ page }) => {
  await mockBankList(page, "USER");
  await page.goto("/app");

  await page.getByRole("button", { name: "打开更多菜单" }).click();
  await expect(page.getByRole("link", { name: "套餐与点数" })).toBeVisible();
  await expect(page.getByRole("link", { name: "导入题库" })).toHaveCount(0);
});

test("signed-in accounts get a bank management entry and page", async ({ page }) => {
  await mockBankList(page, "USER");
  await page.route("**/api/manage/banks", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: [
          {
            id: 11,
            name: "我的题库",
            description: "冲刺用",
            is_public: false,
            unit_count: 2,
            question_count: 10,
          },
        ],
      },
    }),
  );

  await page.goto("/app/banks");
  await expect(page.getByRole("heading", { name: "题库管理" })).toBeVisible();
  await expect(page.getByRole("link", { name: /我的题库/ })).toHaveAttribute(
    "href",
    "/app/banks/11",
  );

  // 普通账号也能在导航里看到入口（手机端收在「更多」里）
  await page.getByRole("button", { name: "打开更多菜单" }).click();
  await expect(page.getByRole("link", { name: "题库管理" })).toBeVisible();
});

test("empty bank management list offers creating the first bank", async ({ page }) => {
  await mockBankList(page, "USER");
  let created: Record<string, unknown> | null = null;
  await page.route("**/api/manage/banks", (route) => {
    if (route.request().method() === "POST") {
      created = route.request().postDataJSON();
      return route.fulfill({ json: { code: 200, message: "ok", data: { id: 11 } } });
    }
    return route.fulfill({ json: { code: 200, message: "ok", data: [] } });
  });

  await page.goto("/app/banks");
  await expect(page.getByText("还没有自己的题库")).toBeVisible();

  await page.getByRole("button", { name: "新建空题库" }).first().click();
  await page.getByPlaceholder("例如：计算机网络-冲刺").fill("我的题库");
  await page.getByRole("button", { name: "创建" }).click();

  expect(created).toMatchObject({ name: "我的题库" });
});

test("empty bank list still lets the account create the first bank", async ({ page }) => {
  await page.route("**/api/me", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: { id: "u", email: "u@example.com", nickname: "u", role: "USER", status: "ENABLED" },
      },
    }),
  );
  await page.route("**/api/points", (route) =>
    route.fulfill({ json: { code: 200, message: "ok", data: { entitlements: {}, ledger: [] } } }),
  );
  await page.route("**/api/banks", (route) =>
    route.fulfill({ json: { code: 200, message: "ok", data: [] } }),
  );
  let created: Record<string, unknown> | null = null;
  await page.route("**/api/manage/banks", (route) => {
    created = route.request().postDataJSON();
    return route.fulfill({ json: { code: 200, message: "ok", data: { id: 11 } } });
  });

  await page.goto("/app");
  await page.getByPlaceholder("新题库名称").fill("我的题库");
  await page.getByRole("button", { name: "新建题库" }).click();

  expect(created).toMatchObject({ name: "我的题库" });
});

test("legal pages are public and linked from the login footer", async ({ page }) => {
  // 未登录：/api/me 返回 401，合规页面仍必须能直接打开
  await page.route("**/api/me", (route) =>
    route.fulfill({ status: 401, json: { code: 401, message: "请先登录", data: null } }),
  );
  await page.route("**/captcha/image", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: { ticket: "t1", imageBase64: "data:image/png;base64,AA==" },
      },
    }),
  );

  await page.goto("/terms");
  await expect(page).toHaveURL(/\/terms$/);
  await expect(page.getByRole("heading", { name: "服务条款" })).toBeVisible();
  await expect(page.getByText("点数长期有效", { exact: false })).toBeVisible();

  await page.goto("/privacy");
  await expect(page).toHaveURL(/\/privacy$/);
  await expect(page.getByRole("heading", { name: "隐私政策" })).toBeVisible();
  await expect(page.getByText("Waffo Pancake", { exact: false })).toBeVisible();

  // 登录页底部那行「使用规则」现在指向真实页面
  await page.goto("/login");
  await page.getByRole("link", { name: "《服务条款》" }).click();
  await expect(page).toHaveURL(/\/terms$/);
  await expect(page.getByRole("heading", { name: "服务条款" })).toBeVisible();
});

test("every app page exposes the legal links in the footer", async ({ page }) => {
  await page.route("**/api/me", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: { id: "u", email: "u@example.com", nickname: "u", role: "USER", status: "ENABLED" },
      },
    }),
  );
  await page.route("**/api/points", (route) =>
    route.fulfill({ json: { code: 200, message: "ok", data: { entitlements: {}, ledger: [] } } }),
  );
  await page.route("**/api/banks", (route) =>
    route.fulfill({ json: { code: 200, message: "ok", data: [] } }),
  );

  await page.goto("/app");
  await expect(page.getByRole("link", { name: "服务条款" }).first()).toHaveAttribute("href", "/terms");
  await expect(page.getByRole("link", { name: "隐私政策" })).toHaveAttribute("href", "/privacy");
  await expect(page.getByText("客服邮箱")).toBeVisible();
  await expect(page.getByRole("link", { name: "sealevel666@163.com" })).toHaveAttribute(
    "href",
    "mailto:sealevel666@163.com",
  );

  // 从页脚真的能点进去
  await page.getByRole("link", { name: "隐私政策" }).click();
  await expect(page).toHaveURL(/\/privacy$/);
});

test.describe("desktop layout", () => {
  test.use({
    viewport: { width: 1440, height: 900 },
    isMobile: false,
    hasTouch: false,
  });

  test("keeps the sidebar and main content side by side", async ({ page }) => {
    await page.route("**/api/me", (route) =>
      route.fulfill({
        json: {
          code: 200,
          message: "ok",
          data: {
            id: "test-user",
            email: "shuati.admin@gmail.com",
            nickname: "admin",
            role: "ADMIN",
            status: "ENABLED",
          },
        },
      }),
    );
    await page.route("**/api/points", (route) =>
      route.fulfill({
        json: {
          code: 200,
          message: "ok",
          data: {
            entitlements: {
              planCode: "free",
              planName: "免费版",
              planExpiresAt: null,
              monthlyQuota: 50,
              monthlyUsed: 0,
              monthlyLeft: 50,
              bonusBalance: 100,
              available: 150,
              lifetimeUsed: 0,
            },
            ledger: [],
          },
        },
      }),
    );
    await page.route("**/api/banks", (route) =>
      route.fulfill({ json: { code: 200, message: "ok", data: [] } }),
    );

    await page.goto("/app");

    const sidebar = page.getByTestId("desktop-sidebar");
    const main = page.locator("main");
    await expect(sidebar).toBeVisible();
    await expect(page.getByTestId("mobile-nav")).toBeHidden();

    const sidebarBox = await sidebar.boundingBox();
    const mainBox = await main.boundingBox();
    expect(sidebarBox).not.toBeNull();
    expect(mainBox).not.toBeNull();
    expect(mainBox!.x).toBeGreaterThanOrEqual(sidebarBox!.x + sidebarBox!.width);
    expect(mainBox!.y).toBe(0);

    const content = page.locator("main > div").first();
    const contentBox = await content.boundingBox();
    expect(contentBox).not.toBeNull();
    expect(contentBox!.width).toBeGreaterThanOrEqual(mainBox!.width * 0.9);
  });
});
