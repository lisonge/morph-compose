<script setup lang="ts">
import { useMutationObserver } from '@vueuse/core';
import { onMounted, onScopeDispose, shallowRef, useTemplateRef } from 'vue';

const playgroundContainer = useTemplateRef<HTMLElement>('playgroundContainer');
const loading = shallowRef(true);
const errorMessage = shallowRef('');
let disposed = false;

const { stop: stopMountObserver } = useMutationObserver(
  playgroundContainer,
  () => {
    if (!playgroundContainer.value?.hasChildNodes()) return;
    loading.value = false;
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
    const { renderMorphPlayground } = await import('morph-playground');
    if (disposed) return;
    renderMorphPlayground(container);
  } catch (cause) {
    errorMessage.value = cause instanceof Error
      ? cause.message
      : 'The Kotlin/Wasm playground could not be loaded.';
    loading.value = false;
  }
});

onScopeDispose(() => {
  disposed = true;
  playgroundContainer.value?.replaceChildren();
});

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
          <span v-if="loading" class="h-7 w-7 animate-spin rounded-full border-2 border-[var(--border)] border-t-[var(--brand)]" />
          <span class="text-sm font-bold">{{ errorMessage || 'Loading Kotlin/Wasm playground…' }}</span>
          <span v-if="errorMessage" class="text-xs leading-5 text-[var(--muted)]">
            Use a current browser with WebAssembly GC support and serve the site over HTTP.
          </span>
        </div>
      </div>
    </div>
  </div>
</template>
