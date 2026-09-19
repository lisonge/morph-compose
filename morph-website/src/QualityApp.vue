<script setup lang="ts">
import { computed, onMounted, onScopeDispose, ref } from 'vue';
import { marked } from 'marked';
import qualityDocument from '../../docs/quality.md?raw';

type Report = { suite: string; available: boolean; state: string; mode?: string; publicImages?: boolean;
  generatedAt?: string; passed?: boolean; cases?: number; changed?: number };
const reports = ref<Report[]>([]);
const error = ref('');
const selected = ref('quick');
const tab = ref(location.hash === '#report' ? 'report' : 'document');
const refreshKey = ref(0);
const active = computed(() => reports.value.find((r) => r.suite === selected.value));
const reportUrl = computed(() => `/quality-reports/${selected.value}/${active.value?.publicImages ? 'public-index.html' : 'index.html'}`);
// This content is a build-time repository document, never external/user HTML.
const documentHtml = marked.parse(qualityDocument);
function navigate(value: string) { tab.value = value; location.hash = value; }
function open(url: string) { window.location.href = url; }
async function refresh() {
  try {
    const response = await fetch('/quality-status.json', { cache: 'no-store' });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    reports.value = await response.json();
    refreshKey.value++;
    error.value = '';
  } catch { error.value = '无法读取报告状态，请检查本地服务或重新部署。'; }
}
function syncTab() { tab.value = location.hash === '#report' ? 'report' : 'document'; }
onMounted(() => { void refresh(); window.addEventListener('hashchange', syncTab); });
onScopeDispose(() => window.removeEventListener('hashchange', syncTab));
</script>

<template>
  <div class="quality-shell">
    <div class="quality-top">
      <div class="quality-brand" @click="open('/')">变 <span>变形动画 / 质量中心</span></div>
      <div class="quality-action" @click="open('/')">动画演示 ↗</div>
    </div>
    <div class="quality-tabs">
      <div :class="{ selected: tab === 'document' }" @click="navigate('document')">质量控制文档</div>
      <div :class="{ selected: tab === 'report' }" @click="navigate('report')">视觉回归报告</div>
      <span>桌面渲染 · 无需加载动画运行时</span>
    </div>
    <div v-if="tab === 'document'" class="quality-scroll">
      <div class="quality-document" v-html="documentHtml" />
    </div>
    <div v-else class="quality-report">
      <div class="report-toolbar">
        <div class="suite-picker">
          <div v-for="suite in ['quick', 'wide']" :key="suite" :class="{ selected: selected === suite }" @click="selected = suite">{{ suite === 'quick' ? '快速回归' : '广域抽样' }}</div>
        </div>
        <div class="report-info" v-if="active?.available">
          <span :class="{ failed: !active.passed }">{{ active.mode === 'compare' ? '基线对比' : '候选生成' }} · {{ active.passed ? '通过' : '未通过' }} · {{ active.cases }} 个序列 · {{ active.changed }} 项不同</span>
          <span>报告快照 {{ active.generatedAt ? new Date(active.generatedAt).toLocaleString('zh-CN') : '时间未知' }} · {{ active.publicImages ? '公开图片链接' : '本地图片' }} · 通过不代表人工视觉认可</span>
        </div>
        <div class="quality-action" @click="refresh">刷新报告</div>
        <div v-if="active?.available" class="quality-action" @click="open(reportUrl)">独立打开 ↗</div>
      </div>
      <div v-if="error" class="report-empty">{{ error }}</div>
      <iframe v-else-if="active?.available" :key="`${selected}-${refreshKey}`" :src="reportUrl" title="视觉回归报告" />
      <div v-else class="report-empty">
        <div>{{ active?.state === 'running' ? '报告正在生成，完成后点击刷新。' : '尚未生成可用报告。' }}</div>
        <span>在仓库根目录执行，完成后点击「刷新报告」：</span>
        <code>{{ selected === 'quick' ? 'pnpm quality:report' : './gradlew.bat :morph-playground:visualRegression -Pmorph.visual.suite=wide -Pmorph.visual.mode=candidate' }}</code>
        <span>广域抽样生成耗时较长；不会因打开页面而自动运行。</span>
      </div>
    </div>
  </div>
</template>

<style>
.quality-shell { height: 100dvh; display: flex; flex-direction: column; }
.quality-top { padding: 16px 28px; display: flex; justify-content: space-between; background: white; border-bottom: 1px solid var(--border); }
.quality-brand { color: var(--brand); font-weight: 800; cursor: pointer; }
.quality-brand span { color: var(--text); margin-left: 12px; }
.quality-action { cursor: pointer; font-size: 13px; color: var(--brand); white-space: nowrap; }
.quality-tabs { display: flex; align-items: center; gap: 24px; padding: 0 28px; border-bottom: 1px solid var(--border); }
.quality-tabs > div { cursor: pointer; padding: 18px 0; border-bottom: 2px solid transparent; font-size: 14px; }
.quality-tabs > .selected { border-color: var(--brand); color: var(--brand); }
.quality-tabs > span { margin-left: auto; font-size: 12px; color: var(--muted); }
.quality-scroll { overflow: auto; flex: 1; }
.quality-document { max-width: 1000px; padding: 30px 32px 80px; margin: auto; line-height: 1.85; font-size: 15px; }
.quality-document h1 { font-size: 30px; margin: 8px 0 30px; }
.quality-document h2 { margin-top: 38px; font-size: 21px; }
.quality-document table { border-collapse: collapse; width: 100%; font-size: 13px; }
.quality-document td, .quality-document th { border: 1px solid var(--border); padding: 10px 12px; text-align: left; }
.quality-document th { background: #eef0f6; }
.quality-document pre { padding: 18px; overflow-x: auto; background: #e9ecf3; border-radius: 8px; line-height: 1.6; }
.quality-document code { font-size: 13px; overflow-wrap: anywhere; }
.quality-document a { color: var(--brand); }
.quality-report { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.report-toolbar { padding: 14px 28px; display: flex; gap: 20px; align-items: center; flex-wrap: wrap; }
.suite-picker { display: flex; border: 1px solid var(--border); border-radius: 7px; overflow: hidden; }
.suite-picker div { cursor: pointer; padding: 7px 14px; font-size: 13px; }
.suite-picker .selected { background: var(--brand); color: white; }
.report-info { flex: 1; font-size: 13px; display: grid; gap: 4px; }
.report-info span + span { color: var(--muted); font-size: 11px; }
.report-info .failed { color: #b33140; }
.quality-report iframe { width: 100%; flex: 1; min-height: 0; border: 0; border-top: 1px solid var(--border); background: white; }
.report-empty { padding: 48px 28px; display: grid; gap: 18px; }
.report-empty span { color: var(--muted); font-size: 14px; }
.report-empty code { overflow-wrap: anywhere; user-select: all; }
@media (max-width: 650px) { .quality-tabs > span { display: none; } .quality-document { padding: 20px 16px; } .quality-top, .report-toolbar { padding: 14px 16px; } }
</style>
