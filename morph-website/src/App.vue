<script setup lang="ts">
import { useMutationObserver } from '@vueuse/core';
import { wasmAssets } from 'morph-playground/wasm-assets';
import { computed, onMounted, onScopeDispose, shallowRef, useTemplateRef } from 'vue';
import {
  createTrackedWasmFetch,
  type WasmDownloadProgress,
} from './wasmDownloadProgress';

const playgroundContainer = useTemplateRef<HTMLElement>('playgroundContainer');
const loading = shallowRef(true);
const errorMessage = shallowRef('');
const loadingPhase = shallowRef<'downloading' | 'starting'>('downloading');
const downloadProgress = shallowRef<WasmDownloadProgress>({
  loadedBytes: 0,
  totalBytes: wasmAssets.reduce((total, asset) => total + asset.byteLength, 0),
  ratio: 0,
  complete: false,
  determinate: true,
});
let disposed = false;
let nativeFetch: typeof window.fetch | undefined;
let trackedFetch: typeof window.fetch | undefined;
let progressFrame = 0;
let pendingProgress: WasmDownloadProgress | undefined;

const progressPercent = computed(() => {
  if (downloadProgress.value.complete) return 100;
  return Math.min(99, Math.floor(downloadProgress.value.ratio * 100));
});
const loadingMessage = computed(() => {
  if (loadingPhase.value === 'starting') return 'Starting playground…';
  if (!downloadProgress.value.determinate) return 'Downloading Kotlin/Wasm runtime…';
  return `Downloading Kotlin/Wasm runtime · ${progressPercent.value}%`;
});
const downloadedBytesLabel = computed(() =>
  `${formatBytes(downloadProgress.value.loadedBytes)} / ${formatBytes(downloadProgress.value.totalBytes)}`,
);

const { stop: stopMountObserver } = useMutationObserver(
  playgroundContainer,
  () => {
    if (!playgroundContainer.value?.hasChildNodes()) return;
    loading.value = false;
    restoreFetch();
    stopMountObserver();
  },
  { childList: true },
);

onMounted(async () => {
  const container = playgroundContainer.value;
  if (!container) {
    errorMessage.value = 'The playground container could not be created.';
    loading.value = false;
    return;
  }

  try {
    nativeFetch = window.fetch;
    trackedFetch = createTrackedWasmFetch(
      nativeFetch.bind(window),
      wasmAssets,
      scheduleProgressUpdate,
    );
    window.fetch = trackedFetch;

    const { renderMorphPlayground } = await import('morph-playground');
    if (disposed) return;
    renderMorphPlayground(container);
  } catch (cause) {
    errorMessage.value = cause instanceof Error
      ? cause.message
      : 'The Kotlin/Wasm playground could not be loaded.';
    loading.value = false;
    restoreFetch();
  }
});

onScopeDispose(() => {
  disposed = true;
  restoreFetch();
  if (progressFrame !== 0) cancelAnimationFrame(progressFrame);
  playgroundContainer.value?.replaceChildren();
});

function scheduleProgressUpdate(progress: WasmDownloadProgress): void {
  pendingProgress = progress;
  if (progressFrame !== 0) return;

  progressFrame = requestAnimationFrame(() => {
    progressFrame = 0;
    if (!pendingProgress) return;
    downloadProgress.value = pendingProgress;
    if (pendingProgress.complete) loadingPhase.value = 'starting';
    pendingProgress = undefined;
  });
}

function restoreFetch(): void {
  if (nativeFetch && trackedFetch && window.fetch === trackedFetch) {
    window.fetch = nativeFetch;
  }
  nativeFetch = undefined;
  trackedFetch = undefined;
}

function formatBytes(byteLength: number): string {
  if (byteLength < 1024) return `${byteLength} B`;
  return `${(byteLength / 1024 / 1024).toFixed(1)} MiB`;
}

function openRepository(): void {
  window.open(
    'https://github.com/lisonge/morph-compose',
    '_blank',
    'noopener,noreferrer',
  );
}
</script>

<template>
  <div class="h-dvh min-h-[520px] flex flex-col bg-[var(--page)] text-[var(--text)]">
    <div class="h-14 shrink-0 border-b border-[var(--border)] bg-[var(--surface)] px-4 sm:px-6">
      <div class="mx-auto h-full max-w-[1600px] flex items-center justify-between gap-4">
        <div class="min-w-0 flex items-center gap-3 text-inherit">
          <span class="grid h-8 w-8 shrink-0 place-items-center rounded-lg bg-[var(--brand)] text-sm text-white font-bold">M</span>
          <span class="min-w-0">
            <span class="block truncate text-sm font-bold tracking-[-0.01em] sm:text-base">Morph Icons for Compose</span>
            <span class="hidden text-[11px] text-[var(--muted)] sm:block">Kotlin · Compose Multiplatform · Wasm</span>
          </span>
        </div>

        <div
          class="cursor-pointer shrink-0 rounded-lg border border-[var(--border)] px-3 py-1.5 text-xs text-[var(--muted)] transition hover:border-[var(--brand)] hover:text-[var(--brand)]"
          @click="openRepository"
        >
          GitHub ↗
        </div>
      </div>
    </div>

    <div class="relative min-h-0 flex flex-1">
      <div ref="playgroundContainer" class="mx-auto min-h-0 min-w-0 max-w-[1600px] flex-1 overflow-hidden" />

      <div
        v-if="loading || errorMessage"
        class="pointer-events-none absolute inset-0 grid place-items-center bg-[var(--page)]/92 px-6 text-center"
      >
        <div class="max-w-md grid justify-items-center gap-3">
          <template v-if="loading">
            <span class="text-sm font-bold">{{ loadingMessage }}</span>
            <div class="h-1.5 w-64 max-w-full overflow-hidden rounded-full bg-[var(--border)]">
              <div
                class="h-full origin-left rounded-full bg-[var(--brand)] transition-transform duration-150 ease-out"
                :class="{ 'animate-pulse': !downloadProgress.determinate || loadingPhase === 'starting' }"
                :style="{ transform: `scaleX(${loadingPhase === 'starting' ? 1 : downloadProgress.ratio})` }"
              />
            </div>
            <span
              v-if="downloadProgress.determinate && loadingPhase === 'downloading'"
              class="text-xs tabular-nums text-[var(--muted)]"
            >
              {{ downloadedBytesLabel }}
            </span>
          </template>
          <span v-else class="text-sm font-bold">{{ errorMessage }}</span>
          <span v-if="errorMessage" class="text-xs leading-5 text-[var(--muted)]">
            Use a current browser with WebAssembly GC support and serve the site over HTTP.
          </span>
        </div>
      </div>
    </div>
  </div>
</template>
