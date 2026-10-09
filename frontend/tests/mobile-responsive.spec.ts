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
  await page.getByPlaceholder("请输入图中字符").fill("AB3D");
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
  await page.getByPlaceholder("请输入图中字符").fill("AB3D");
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
  await page.getByPlaceholder("请输入图中字符").fill("AB3D");
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
  await page.getByRole("button", { name: "忘记密码" }).click();
  await page.getByRole("textbox", { name: "邮箱", exact: true }).fill("reset@example.com");
  await page.getByPlaceholder("请输入图中字符").fill("AB3D");
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
