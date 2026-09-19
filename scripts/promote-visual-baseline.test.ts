import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { promoteVisualBaseline } from './promote-visual-baseline.ts';

const hash = (text: string) => createHash('sha256').update(text).digest('hex');

test('推广保存链接、摘要和原因，并拒绝未验证的候选报告', () => {
  const root = mkdtempSync(join(tmpdir(), 'morph-promote-'));
  try {
    const report = join(root, 'morph-playground/build/reports/morph-visual/quick');
    const candidate = join(report, 'candidate');
    const baseline = join(root, 'visual-baselines/quick');
    mkdirSync(candidate, { recursive: true });
    writeFileSync(join(candidate, 'manifest.txt'), 'header\r\ncase_1\t配置\r\n');
    writeFileSync(join(candidate, 'environment.txt'), 'test environment');
    writeFileSync(join(candidate, 'validation.txt'), 'PASS\r\n');
    writeFileSync(join(candidate, 'case_1.png'), 'image bytes');
    writeFileSync(join(report, 'index.html'), 'report');
    writeFileSync(join(report, 'summary.json'), JSON.stringify({ state: 'complete', generatedAt: 'test-run' }));
    const published = {
      generatedAt: 'test-run', sourceSha256: hash('report'),
      images: { 'candidate/case_1.png': 'https://github.com/user-attachments/assets/test-id' },
      sha256: { 'candidate/case_1.png': hash('image bytes') },
    };
    const savePublished = () => writeFileSync(join(report, 'public-images.json'), JSON.stringify(published));
    savePublished();
    assert.throws(() => promoteVisualBaseline(root, '../quick', '测试'), /套件/);
    assert.throws(() => promoteVisualBaseline(root, 'quick', '  '), /原因/);
    assert.equal(existsSync(baseline), false);
    assert.equal(promoteVisualBaseline(root, 'quick', '确认视觉变化'), 1);
    const metadataPath = join(baseline, 'images.json');
    const before = readFileSync(metadataPath, 'utf8');
    const metadata = JSON.parse(before);
    assert.equal(metadata.manifestSha256, hash('header\ncase_1\t配置\n'));
    assert.deepEqual(metadata.images.case_1, { url: published.images['candidate/case_1.png'], sha256: hash('image bytes') });
    assert.match(readFileSync(join(baseline, 'review-log.txt'), 'utf8'), /1 个案例 \| 确认视觉变化/);
    // 再次更新覆盖已有元数据，在 Windows 上也必须成功。
    assert.equal(promoteVisualBaseline(root, 'quick', '重复验证'), 1);
    published.generatedAt = 'old-run';
    savePublished();
    assert.throws(() => promoteVisualBaseline(root, 'quick', '测试'), /公开链接不属于/);
    published.generatedAt = 'test-run';
    savePublished();
    writeFileSync(join(candidate, 'case_1.png'), 'changed bytes');
    assert.throws(() => promoteVisualBaseline(root, 'quick', '测试'), /发布后被修改/);
    writeFileSync(join(candidate, 'validation.txt'), 'FAIL\n');
    assert.throws(() => promoteVisualBaseline(root, 'quick', '测试'), /技术检查/);
    assert.equal(readFileSync(metadataPath, 'utf8'), before);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
