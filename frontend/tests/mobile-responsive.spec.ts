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
