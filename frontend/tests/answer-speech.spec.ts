import { expect, test, type Page } from "@playwright/test";

const QUESTION_WITH_CODE =
  "参考答案第一段。\n\n```java\npublic class A { private B b; }\n```\n\n解析最后一段。";

type MockVoice = Pick<
  SpeechSynthesisVoice,
  "default" | "lang" | "localService" | "name" | "voiceURI"
>;

interface MockSpeechOptions {
  voices?: MockVoice[];
  failureByVoiceURI?: Record<string, string>;
  selectedVoiceURI?: string;
}

const DEFAULT_TEST_VOICE: MockVoice = {
  default: true,
  lang: "zh-CN",
  localService: true,
  name: "中文测试音色",
  voiceURI: "zh-CN-test",
};

async function mockChineseSpeech(
  page: Page,
  options: MockSpeechOptions = {},
) {
  const voices = options.voices ?? [DEFAULT_TEST_VOICE];
  await page.addInitScript(({ mockVoices, failureByVoiceURI, selectedVoiceURI }) => {
    if (selectedVoiceURI) {
      window.sessionStorage.setItem("shuati.speech.voice", selectedVoiceURI);
    }
    class FakeUtterance {
      text: string;
      lang = "";
      rate = 1;
      voice: SpeechSynthesisVoice | null = null;
      onend: ((event: SpeechSynthesisEvent) => void) | null = null;
      onerror: ((event: SpeechSynthesisErrorEvent) => void) | null = null;

      constructor(text: string) {
        this.text = text;
      }
    }

    class FakeSpeechSynthesis extends EventTarget {
      current: FakeUtterance | null = null;
      spoken: Array<{ text: string; rate: number; lang: string }> = [];
      cancelled = 0;
      paused = false;
      speaking = false;
      pending = false;
      voices = mockVoices as SpeechSynthesisVoice[];

      getVoices() {
        return this.voices;
      }

      speak(utterance: FakeUtterance) {
        this.current = utterance;
        this.speaking = true;
        this.paused = false;
        this.spoken.push({
          text: utterance.text,
          rate: utterance.rate,
          lang: utterance.lang,
          voice: utterance.voice?.name ?? "",
        });

        const failure = failureByVoiceURI[utterance.voice?.voiceURI ?? ""];
        if (failure) {
          window.setTimeout(() => {
            if (this.current !== utterance) return;
            this.current = null;
            this.speaking = false;
            utterance.onerror?.({
              error: failure,
              message: `mock ${failure}`,
            } as SpeechSynthesisErrorEvent);
          }, 0);
        }
      }

      pause() {
        this.paused = true;
      }

      resume() {
        this.paused = false;
      }

      cancel() {
        this.cancelled += 1;
        this.current = null;
        this.speaking = false;
        this.paused = false;
      }
    }

    const synth = new FakeSpeechSynthesis();
    Object.defineProperty(window, "speechSynthesis", {
      configurable: true,
      value: synth,
    });
    Object.defineProperty(window, "SpeechSynthesisUtterance", {
      configurable: true,
      value: FakeUtterance,
    });
    Object.assign(window, {
      __finishSpeech() {
        const current = synth.current;
        synth.current = null;
        synth.speaking = false;
        current?.onend?.({} as SpeechSynthesisEvent);
      },
      __speechState() {
        return {
          cancelled: synth.cancelled,
          paused: synth.paused,
          speaking: synth.speaking,
          spoken: synth.spoken,
        };
      },
    });
  }, {
    mockVoices: voices,
    failureByVoiceURI: options.failureByVoiceURI ?? {},
    selectedVoiceURI: options.selectedVoiceURI ?? "",
  });
}

async function mockPracticePage(page: Page, explanation = QUESTION_WITH_CODE) {
  await page.route("**/api/me", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: {
          id: "speech-user",
          email: "speech@example.com",
          nickname: "speech",
          role: "USER",
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
  await page.route("**/api/practice/last-scope", (route) =>
    route.fulfill({ json: { code: 200, message: "ok", data: {} } }),
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
            content: "语音朗读测试题干",
            options: [{ key: "A", text: "选项 A" }],
            difficulty: "MEDIUM",
            images: null,
            tags: null,
            status: "ON",
            explanation,
          },
          {
            id: 2,
            unit_id: 1,
            type: "SINGLE",
            content: "下一道题",
            options: [{ key: "A", text: "选项 A" }],
            difficulty: "MEDIUM",
            images: null,
            tags: null,
            status: "ON",
            explanation: null,
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
      json: {
        code: 200,
        message: "ok",
        data: { total: 2, answered: 0, correct: 0, answeredIds: [] },
      },
    }),
  );
  await page.route("**/api/progress/cursor*", (route) =>
    route.fulfill({ json: { code: 200, message: "ok", data: null } }),
  );
  await page.route("**/api/practice/submit-choice", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: {
          recordId: 1,
          verdict: "CORRECT",
          score: 10,
          correctAnswer: "A",
        },
      },
    }),
  );
  await page.route("**/api/practice/questions/1/answer", (route) =>
    route.fulfill({
      json: { code: 200, message: "ok", data: { answer: "背题模式参考答案。" } },
    }),
  );
  await page.route("**/api/practice/questions/1/explanation", (route) =>
    route.fulfill({
      json: {
        code: 200,
        message: "ok",
        data: { explanation: "重新生成的第一段。重新生成的第二段。" },
      },
    }),
  );
}

async function openAnsweredQuestion(page: Page, explanation?: string) {
  await mockChineseSpeech(page);
  await mockPracticePage(page, explanation);
  await page.goto("/app/practice?unitId=1");
  await page.getByRole("button", { name: /选项 A/ }).click();
  await page.getByRole("button", { name: "提交答案" }).click();
  await expect(page.getByTestId("answer-speech-player")).toBeVisible();
}

test("answer speech supports play, pause, resume and stop", async ({ page }) => {
  await openAnsweredQuestion(page);
  expect((await page.evaluate(() => (window as any).__speechState())).spoken).toHaveLength(0);

  await page.getByRole("button", { name: "朗读答案与解析" }).click();
  await expect(page.getByRole("button", { name: "暂停朗读" })).toBeVisible({ timeout: 1_000 });
  await expect(page.getByTestId("speech-current-segment")).toContainText("参考答案第一段");

  await page.getByRole("button", { name: "暂停朗读" }).click();
  await expect(page.getByRole("button", { name: "继续朗读" })).toBeVisible({ timeout: 1_000 });
  expect((await page.evaluate(() => (window as any).__speechState())).paused).toBe(true);

  await page.getByRole("button", { name: "继续朗读" }).click();
  await expect(page.getByRole("button", { name: "暂停朗读" })).toBeVisible({ timeout: 1_000 });

  await page.getByRole("button", { name: "停止朗读" }).click();
  await expect(page.getByRole("button", { name: "朗读答案与解析" })).toBeVisible({ timeout: 1_000 });
  expect((await page.evaluate(() => (window as any).__speechState())).cancelled).toBeGreaterThan(0);
});

test("failed online voice is removed and falls back to a working local voice", async ({ page }) => {
  const onlineVoice: MockVoice = {
    default: true,
    lang: "zh-CN",
    localService: false,
    name: "Remote Cloud Voice",
    voiceURI: "remote-cloud-voice",
  };
  const localVoice: MockVoice = {
    default: false,
    lang: "zh-CN",
    localService: true,
    name: "Microsoft Huihui Desktop",
    voiceURI: "microsoft-huihui-desktop",
  };

  await mockChineseSpeech(page, {
    voices: [onlineVoice, localVoice],
    failureByVoiceURI: { [onlineVoice.voiceURI]: "synthesis-failed" },
    selectedVoiceURI: onlineVoice.voiceURI,
  });
  await mockPracticePage(page);
  await page.goto("/app/practice?unitId=1");
  await page.getByRole("button", { name: /选项 A/ }).click();
  await page.getByRole("button", { name: "提交答案" }).click();
  await page.getByRole("button", { name: "朗读答案与解析" }).click();

  await expect(page.getByTestId("speech-voice-notice")).toContainText("已切换");
  await expect(page.getByRole("button", { name: "暂停朗读" })).toBeVisible({ timeout: 1_000 });
  await expect(page.getByLabel("朗读音色")).toHaveCount(0);
  await expect(page.getByText(onlineVoice.name)).toHaveCount(0);
  await expect.poll(async () =>
    page.evaluate(() => sessionStorage.getItem("shuati.speech.unavailableVoices")),
  ).toContain(onlineVoice.voiceURI);
  await expect.poll(async () =>
    page.evaluate(() => (window as any).__speechState().spoken.at(-1)?.voice),
  ).toBe(localVoice.name);

  await page.reload();
  await page.getByRole("button", { name: /选项 A/ }).click();
  await page.getByRole("button", { name: "提交答案" }).click();
  await expect(page.getByText(onlineVoice.name)).toHaveCount(0);
  await page.getByRole("button", { name: "朗读答案与解析" }).click();
  await expect.poll(async () =>
    page.evaluate(() => (window as any).__speechState().spoken.at(-1)?.voice),
  ).toBe(localVoice.name);
});

test("online natural voice is hidden while a local Chinese voice is available", async ({ page }) => {
  const onlineVoice: MockVoice = {
    default: true,
    lang: "zh-CN",
    localService: false,
    name: "Microsoft 晓晓 Online (Natural) - Chinese (Mandarin, Simplified)",
    voiceURI: "microsoft-xiaoxiao-online",
  };
  const localVoice: MockVoice = {
    default: false,
    lang: "zh-CN",
    localService: true,
    name: "Microsoft Huihui Desktop",
    voiceURI: "microsoft-huihui-desktop",
  };

  await mockChineseSpeech(page, {
    voices: [onlineVoice, localVoice],
    selectedVoiceURI: onlineVoice.voiceURI,
  });
  await mockPracticePage(page);
  await page.goto("/app/practice?unitId=1");
  await page.getByRole("button", { name: /选项 A/ }).click();
  await page.getByRole("button", { name: "提交答案" }).click();

  await expect(page.getByText(onlineVoice.name)).toHaveCount(0);
  await expect(page.getByLabel("朗读音色")).toHaveCount(0);
  await page.getByRole("button", { name: "朗读答案与解析" }).click();
  await expect.poll(async () =>
    page.evaluate(() => (window as any).__speechState().spoken.at(-1)?.voice),
  ).toBe(localVoice.name);
});

test("autoplay restriction does not hide a valid voice", async ({ page }) => {
  const alternateVoice: MockVoice = {
    default: false,
    lang: "zh-CN",
    localService: true,
    name: "中文备用音色",
    voiceURI: "zh-CN-backup",
  };
  await mockChineseSpeech(page, {
    voices: [DEFAULT_TEST_VOICE, alternateVoice],
    failureByVoiceURI: { [DEFAULT_TEST_VOICE.voiceURI]: "not-allowed" },
  });
  await mockPracticePage(page);
  await page.goto("/app/practice?unitId=1");
  await page.getByRole("button", { name: /选项 A/ }).click();
  await page.getByRole("button", { name: "提交答案" }).click();
  await page.getByRole("button", { name: "朗读答案与解析" }).click();

  await expect(page.getByText("浏览器阻止了语音播放，请再次点击朗读")).toBeVisible();
  await expect(page.getByLabel("朗读音色").locator("option")).toHaveCount(2);
  await expect(page.getByLabel("朗读音色")).toHaveValue(DEFAULT_TEST_VOICE.voiceURI);
});

test("answer speech applies rate and code preference", async ({ page }) => {
  await openAnsweredQuestion(page);

  await page.getByLabel("朗读语速").selectOption("1.5");
  await page.getByRole("button", { name: "朗读答案与解析" }).click();

  await expect.poll(async () =>
    page.evaluate(() => (window as any).__speechState().spoken.at(-1)?.rate),
  ).toBe(1.5);

  await page.evaluate(() => (window as any).__finishSpeech());
  await expect(page.getByTestId("speech-current-segment")).toContainText("此处有一段代码");

  await page.getByRole("button", { name: "停止朗读" }).click();
  await page.getByRole("checkbox", { name: "朗读代码" }).check();
  await page.getByRole("button", { name: "朗读答案与解析" }).click();
  await page.evaluate(() => (window as any).__finishSpeech());
  await expect(page.getByTestId("speech-current-segment")).toContainText("public class A");

  await page.reload();
  await page.getByRole("button", { name: /选项 A/ }).click();
  await page.getByRole("button", { name: "提交答案" }).click();
  await expect(page.getByLabel("朗读语速")).toHaveValue("1.5");
  await expect(page.getByRole("checkbox", { name: "朗读代码" })).toBeChecked();
});

test("answer speech reads clean markdown text instead of syntax", async ({ page }) => {
  await openAnsweredQuestion(
    page,
    "## 解析步骤\n\n1. 阅读[官方文档](https://example.com/docs)。\n2. 再复述一遍。",
  );

  await page.getByRole("button", { name: "朗读答案与解析" }).click();
  await expect(page.getByTestId("speech-current-segment")).toHaveText("解析步骤。");

  await page.evaluate(() => (window as any).__finishSpeech());
  await expect(page.getByTestId("speech-current-segment")).toHaveText("阅读官方文档。");
  await expect(page.getByTestId("speech-current-segment")).not.toContainText("https://");
  await expect(page.getByTestId("speech-current-segment")).not.toContainText("1.");

  await page.evaluate(() => (window as any).__finishSpeech());
  await expect(page.getByTestId("speech-current-segment")).toHaveText("再复述一遍。");
});

test("regenerating explanation stops old speech and reloads the script", async ({ page }) => {
  await openAnsweredQuestion(page);
  await page.getByRole("button", { name: "朗读答案与解析" }).click();

  const before = await page.evaluate(() => (window as any).__speechState().cancelled);
  await page.getByRole("button", { name: "重新生成解析" }).click();

  await expect.poll(async () =>
    page.evaluate(() => (window as any).__speechState().cancelled),
  ).toBeGreaterThan(before);
  await expect(page.getByRole("button", { name: "朗读答案与解析" })).toBeVisible();

  await page.getByRole("button", { name: "朗读答案与解析" }).click();
  await expect(page.getByTestId("speech-current-segment")).toHaveText("重新生成的第一段。");
});

test("switching question cancels active speech", async ({ page }) => {
  await openAnsweredQuestion(page);
  await page.getByRole("button", { name: "朗读答案与解析" }).click();

  const before = await page.evaluate(() => (window as any).__speechState().cancelled);
  await page.getByRole("button", { name: /下一题/ }).click();
  await expect.poll(async () =>
    page.evaluate(() => (window as any).__speechState().cancelled),
  ).toBeGreaterThan(before);
  await expect(page.getByTestId("answer-speech-player")).toHaveCount(0);
});

test("page visibility change cancels active speech", async ({ page }) => {
  await openAnsweredQuestion(page);
  await page.getByRole("button", { name: "朗读答案与解析" }).click();

  const before = await page.evaluate(() => (window as any).__speechState().cancelled);
  await page.evaluate(() => {
    Object.defineProperty(document, "hidden", { configurable: true, value: true });
    document.dispatchEvent(new Event("visibilitychange"));
  });
  await expect.poll(async () =>
    page.evaluate(() => (window as any).__speechState().cancelled),
  ).toBeGreaterThan(before);
});

test("study mode reuses the same speech player", async ({ page }) => {
  await mockChineseSpeech(page);
  await mockPracticePage(page);
  await page.goto("/app/practice?unitId=1");
  await page.getByRole("button", { name: "背题模式" }).click();

  await expect(page.getByTestId("answer-speech-player")).toBeVisible();
  await page.getByRole("button", { name: "朗读答案与解析" }).click();
  await expect(page.getByTestId("speech-current-segment")).toContainText("背题模式参考答案");
});

test("unsupported speech synthesis shows a clear fallback", async ({ page }) => {
  await page.addInitScript(() => {
    Object.defineProperty(window, "speechSynthesis", {
      configurable: true,
      value: undefined,
    });
  });
  await mockPracticePage(page);
  await page.goto("/app/practice?unitId=1");
  await page.getByRole("button", { name: /选项 A/ }).click();
  await page.getByRole("button", { name: "提交答案" }).click();

  await expect(page.getByText("当前设备不支持语音朗读")).toBeVisible();
  await expect(page.getByRole("button", { name: "朗读答案与解析" })).toHaveCount(0);
});

test("missing Chinese voice shows an explicit error", async ({ page }) => {
  await page.addInitScript(() => {
    Object.defineProperty(window, "speechSynthesis", {
      configurable: true,
      value: {
        cancel() {},
        getVoices: () => [],
        pause() {},
        resume() {},
        speak() {},
      },
    });
    Object.defineProperty(window, "SpeechSynthesisUtterance", {
      configurable: true,
      value: class {
        text: string;
        constructor(text: string) {
          this.text = text;
        }
      },
    });
  });
  await mockPracticePage(page);
  await page.goto("/app/practice?unitId=1");
  await page.getByRole("button", { name: /选项 A/ }).click();
  await page.getByRole("button", { name: "提交答案" }).click();
  await page.getByRole("button", { name: "朗读答案与解析" }).click();

  await expect(page.getByText("设备未安装中文语音")).toBeVisible();
});
