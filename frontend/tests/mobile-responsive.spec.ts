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
