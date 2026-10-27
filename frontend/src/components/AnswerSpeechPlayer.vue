<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { Pause, Play, Square, Volume2 } from "lucide-vue-next";
import {
  buildSpeechSegments,
  type SpeechSegment,
} from "@/components/speechText";

const props = defineProps<{
  contentKey: string | number;
  referenceAnswer?: string;
  feedback?: string;
  hitPoints?: string[];
  missedPoints?: string[];
  explanation?: string;
}>();

type SpeechState = "idle" | "playing" | "paused" | "unsupported" | "error";
type SpeechRate = "0.75" | "1" | "1.25" | "1.5";

const RATE_KEY = "shuati.speech.rate";
const READ_CODE_KEY = "shuati.speech.readCode";
const VOICE_KEY = "shuati.speech.voice";

const synth = typeof window !== "undefined" ? window.speechSynthesis : undefined;
const supported = Boolean(
  synth && typeof window !== "undefined" && typeof window.SpeechSynthesisUtterance === "function",
);

const state = ref<SpeechState>(supported ? "idle" : "unsupported");
const errorMessage = ref("");
const currentIndex = ref(-1);
const availableVoices = ref<SpeechSynthesisVoice[]>([]);
const selectedVoiceURI = ref(readPreference(VOICE_KEY) ?? "");
const rate = ref<SpeechRate>(normalizeRate(readPreference(RATE_KEY)));
const readCode = ref(readPreference(READ_CODE_KEY) === "true");
let activeUtterance: SpeechSynthesisUtterance | null = null;
let playbackGeneration = 0;

const hasContent = computed(() =>
  Boolean(
    props.referenceAnswer
      || props.feedback
      || props.hitPoints?.length
      || props.missedPoints?.length
      || props.explanation,
  ),
);

const segments = computed<SpeechSegment[]>(() =>
  buildSpeechSegments(
    {
      referenceAnswer: props.referenceAnswer,
      feedback: props.feedback,
      hitPoints: props.hitPoints,
      missedPoints: props.missedPoints,
      explanation: props.explanation,
    },
    { readCode: readCode.value },
  ),
);

const chineseVoices = computed(() =>
  availableVoices.value.filter((voice) => /^zh(?:-|_)/i.test(voice.lang)),
);

const selectedVoice = computed(() =>
  chineseVoices.value.find((voice) => voice.voiceURI === selectedVoiceURI.value)
  ?? chineseVoices.value.find((voice) => voice.default)
  ?? chineseVoices.value[0]
  ?? null,
);

const currentSegment = computed(() =>
  currentIndex.value >= 0 ? segments.value[currentIndex.value] ?? null : null,
);

const statusText = computed(() => {
  if (state.value === "unsupported") return "当前设备不支持语音朗读";
  if (state.value === "error") return errorMessage.value;
  if (state.value === "paused") return "朗读已暂停";
  if (state.value === "playing") {
    return `正在朗读第 ${currentIndex.value + 1} / ${segments.value.length} 段`;
  }
  return segments.value.length ? `共 ${segments.value.length} 段` : "暂无可朗读内容";
});

function readPreference(key: string): string | null {
  try {
    return window.sessionStorage.getItem(key);
  } catch {
    return null;
  }
}

function writePreference(key: string, value: string) {
  try {
    window.sessionStorage.setItem(key, value);
  } catch {
    // 隐私模式下可能禁用 sessionStorage，不影响当前会话播放。
  }
}

function normalizeRate(value: string | null): SpeechRate {
  return value === "0.75" || value === "1.25" || value === "1.5" ? value : "1";
}

function refreshVoices() {
  if (!synth) return;
  availableVoices.value = synth.getVoices();
  if (!selectedVoiceURI.value && selectedVoice.value) {
    selectedVoiceURI.value = selectedVoice.value.voiceURI;
  }
}

async function ensureVoices(): Promise<SpeechSynthesisVoice[]> {
  if (!synth) return [];
  refreshVoices();
  if (availableVoices.value.length) return availableVoices.value;
  await new Promise<void>((resolve) => {
    let settled = false;
    const eventTarget = synth as unknown as EventTarget;
    const finish = () => {
      if (settled) return;
      settled = true;
      eventTarget.removeEventListener?.("voiceschanged", finish);
      resolve();
    };
    eventTarget.addEventListener?.("voiceschanged", finish, { once: true });
    window.setTimeout(finish, 800);
  });
  refreshVoices();
  return availableVoices.value;
}

function cancelPlayback(resetState = true) {
  playbackGeneration += 1;
  activeUtterance = null;
  synth?.cancel();
  if (resetState) {
    currentIndex.value = -1;
    state.value = supported ? "idle" : "unsupported";
  }
}

async function start() {
  if (!supported || !synth) {
    state.value = "unsupported";
    return;
  }
  errorMessage.value = "";
  const voices = await ensureVoices();
  if (!voices.some((voice) => /^zh(?:-|_)/i.test(voice.lang))) {
    state.value = "error";
    errorMessage.value = "设备未安装中文语音";
    return;
  }
  if (!segments.value.length) {
    state.value = "idle";
    return;
  }
  cancelPlayback(false);
  currentIndex.value = 0;
  speakCurrent(playbackGeneration);
}

function speakCurrent(generation: number) {
  if (!synth || generation !== playbackGeneration) return;
  const segment = segments.value[currentIndex.value];
  if (!segment) {
    cancelPlayback();
    return;
  }

  const utterance = new SpeechSynthesisUtterance(segment.text);
  utterance.lang = selectedVoice.value?.lang || "zh-CN";
  utterance.voice = selectedVoice.value;
  utterance.rate = Number(rate.value);
  utterance.onend = () => {
    if (generation !== playbackGeneration || state.value !== "playing") return;
    currentIndex.value += 1;
    speakCurrent(generation);
  };
  utterance.onerror = (event) => {
    if (
      generation !== playbackGeneration
      || event.error === "canceled"
      || event.error === "interrupted"
    ) {
      return;
    }
    activeUtterance = null;
    state.value = "error";
    errorMessage.value = "语音朗读失败，请重试";
  };
  activeUtterance = utterance;
  state.value = "playing";
  synth.speak(utterance);
}

function pause() {
  if (state.value !== "playing" || !synth) return;
  synth.pause();
  state.value = "paused";
}

function resume() {
  if (state.value !== "paused" || !synth) return;
  synth.resume();
  state.value = "playing";
}

function stop() {
  cancelPlayback();
  errorMessage.value = "";
}

function togglePlayback() {
  if (state.value === "playing") {
    pause();
  } else if (state.value === "paused") {
    resume();
  } else {
    void start();
  }
}

function changeRate(event: Event) {
  const value = (event.target as HTMLSelectElement).value;
  rate.value = normalizeRate(value);
  writePreference(RATE_KEY, rate.value);
  if (state.value === "playing" || state.value === "paused") {
    stop();
  }
}

function changeReadCode(event: Event) {
  readCode.value = (event.target as HTMLInputElement).checked;
  writePreference(READ_CODE_KEY, String(readCode.value));
  stop();
}

function changeVoice(event: Event) {
  selectedVoiceURI.value = (event.target as HTMLSelectElement).value;
  writePreference(VOICE_KEY, selectedVoiceURI.value);
  stop();
}

function handleVisibilityChange() {
  if (document.hidden) stop();
}

onMounted(() => {
  refreshVoices();
  (synth as unknown as EventTarget | undefined)?.addEventListener?.(
    "voiceschanged",
    refreshVoices,
  );
  document.addEventListener("visibilitychange", handleVisibilityChange);
});

onBeforeUnmount(() => {
  stop();
  (synth as unknown as EventTarget | undefined)?.removeEventListener?.(
    "voiceschanged",
    refreshVoices,
  );
  document.removeEventListener("visibilitychange", handleVisibilityChange);
});

watch(
  () => props.contentKey,
  () => stop(),
);

watch(
  segments,
  () => {
    if (state.value === "playing" || state.value === "paused") stop();
  },
);
</script>

<template>
  <section
    v-if="hasContent"
    data-testid="answer-speech-player"
    class="mt-4 rounded-xl border border-mist bg-paper/80 p-3"
    aria-label="答案语音朗读"
  >
    <div class="flex flex-wrap items-center gap-2">
      <Volume2 class="h-4 w-4 shrink-0 text-moss" aria-hidden="true" />
      <span class="mr-auto text-sm font-medium text-ink">语音朗读</span>
      <label v-if="state !== 'unsupported'" class="flex items-center gap-1.5 text-xs text-oat">
        语速
        <select
          :value="rate"
          aria-label="朗读语速"
          class="rounded-lg border border-mist bg-white px-2 py-1.5 text-xs text-ink outline-none focus:border-moss"
          @change="changeRate"
        >
          <option value="0.75">0.75x</option>
          <option value="1">1x</option>
          <option value="1.25">1.25x</option>
          <option value="1.5">1.5x</option>
        </select>
      </label>
      <label
        v-if="state !== 'unsupported' && chineseVoices.length > 1"
        class="flex items-center gap-1.5 text-xs text-oat"
      >
        音色
        <select
          :value="selectedVoice?.voiceURI"
          aria-label="朗读音色"
          class="max-w-36 rounded-lg border border-mist bg-white px-2 py-1.5 text-xs text-ink outline-none focus:border-moss"
          @change="changeVoice"
        >
          <option v-for="voice in chineseVoices" :key="voice.voiceURI" :value="voice.voiceURI">
            {{ voice.name }}
          </option>
        </select>
      </label>
      <label v-if="state !== 'unsupported'" class="flex items-center gap-1.5 text-xs text-oat">
        <input
          type="checkbox"
          :checked="readCode"
          class="h-3.5 w-3.5 accent-moss"
          @change="changeReadCode"
        />
        朗读代码
      </label>
    </div>

    <div class="mt-3 flex flex-wrap items-center gap-2">
      <button
        v-if="state !== 'unsupported'"
        type="button"
        class="flex items-center gap-1.5 rounded-lg bg-ink px-3 py-2 text-xs text-white transition hover:bg-ink/90"
        :aria-label="
          state === 'playing'
            ? '暂停朗读'
            : state === 'paused'
              ? '继续朗读'
              : '朗读答案与解析'
        "
        @click="togglePlayback"
      >
        <Pause v-if="state === 'playing'" class="h-3.5 w-3.5" />
        <Play v-else class="h-3.5 w-3.5" />
        {{ state === "playing" ? "暂停" : state === "paused" ? "继续" : "朗读" }}
      </button>
      <button
        v-if="state === 'playing' || state === 'paused'"
        type="button"
        aria-label="停止朗读"
        class="flex items-center gap-1.5 rounded-lg border border-mist bg-white px-3 py-2 text-xs text-ink transition hover:border-rose hover:text-rose"
        @click="stop"
      >
        <Square class="h-3.5 w-3.5" />
        停止
      </button>
      <span
        class="text-xs"
        :class="state === 'error' ? 'text-rose' : 'text-oat'"
        role="status"
      >
        {{ statusText }}
      </span>
    </div>

    <p
      v-if="currentSegment"
      data-testid="speech-current-segment"
      aria-live="polite"
      class="mt-3 rounded-lg border border-moss/20 bg-moss/8 px-3 py-2 text-sm leading-6 text-ink"
    >
      {{ currentSegment.text }}
    </p>
  </section>
</template>
