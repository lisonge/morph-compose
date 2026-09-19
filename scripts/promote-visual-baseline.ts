import { createHash } from 'node:crypto';
import { appendFileSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';

const sha256 = (data: string | Buffer) => createHash('sha256').update(data).digest('hex');
const readJson = (path: string): Record<string, unknown> => JSON.parse(readFileSync(path, 'utf8'));

export function promoteVisualBaseline(repoRoot: string, suite: string, reason: string): number {
  if (suite !== 'quick' && suite !== 'wide') throw new Error('套件只能是 quick 或 wide。');
  if (!reason.trim() || /[\r\n]/.test(reason)) throw new Error('必须填写非空、单行的基线更新原因。');
  const reportRoot = join(repoRoot, 'morph-playground/build/reports/morph-visual', suite);
  const candidateRoot = join(reportRoot, 'candidate');
  const baselineRoot = join(repoRoot, 'visual-baselines', suite);
  const manifest = readFileSync(join(candidateRoot, 'manifest.txt'), 'utf8');
  if (readFileSync(join(candidateRoot, 'validation.txt'), 'utf8').split(/\r?\n/)[0] !== 'PASS') {
    throw new Error('候选报告未完成技术检查。');
  }
  const summary = readJson(join(reportRoot, 'summary.json'));
  const published = readJson(join(reportRoot, 'public-images.json'));
  if (summary.state !== 'complete' || typeof summary.generatedAt !== 'string'
    || published.generatedAt !== summary.generatedAt
    || published.sourceSha256 !== sha256(readFileSync(join(reportRoot, 'index.html')))) {
    throw new Error('公开链接不属于当前报告，请先执行 pnpm quality:publish。');
  }
  const lines = manifest.replaceAll('\r\n', '\n').split('\n');
  if (lines.at(-1) === '') lines.pop();
  const caseIds = lines.slice(1).map(line => line.split('\t', 1)[0]);
  if (!caseIds.length || new Set(caseIds).size !== caseIds.length) {
    throw new Error('案例清单为空或包含重复编号。');
  }
  const urls = published.images as Record<string, unknown> | undefined;
  const hashes = published.sha256 as Record<string, unknown> | undefined;
  const images: Record<string, { url: string; sha256: string }> = Object.create(null);
  for (const caseId of caseIds) {
    if (!/^[0-9A-Za-z_]+$/.test(caseId)) throw new Error(`无效案例编号：${caseId}`);
    const key = `candidate/${caseId}.png`;
    const hash = sha256(readFileSync(join(candidateRoot, `${caseId}.png`)));
    const url = urls?.[key];
    if (hashes?.[key] !== hash || typeof url !== 'string'
      || !/^https:\/\/github\.com\/user-attachments\/assets\/[0-9a-zA-Z-]+$/.test(url)) {
      throw new Error(`案例缺少已验证的公开链接，或图片在发布后被修改：${caseId}`);
    }
    images[caseId] = { url, sha256: hash };
  }
  const metadata = { version: 1, manifestSha256: sha256(manifest.replaceAll('\r\n', '\n')), images };
  const environment = readFileSync(join(candidateRoot, 'environment.txt'));
  mkdirSync(baselineRoot, { recursive: true });
  const tempPath = join(baselineRoot, 'images.json.tmp');
  writeFileSync(tempPath, `${JSON.stringify(metadata, null, 2)}\n`);
  writeFileSync(join(baselineRoot, 'environment.txt'), environment);
  writeFileSync(join(baselineRoot, 'manifest.txt'), manifest);
  // 最后替换链接元数据，中断的更新会触发清单摘要校验失败。
  renameSync(tempPath, join(baselineRoot, 'images.json'));
  appendFileSync(join(baselineRoot, 'review-log.txt'), `${new Date().toISOString()} | ${caseIds.length} 个案例 | ${reason}\n`);
  return caseIds.length;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const { values } = parseArgs({ options: { suite: { type: 'string', default: 'quick' }, reason: { type: 'string' } } });
    const count = promoteVisualBaseline(fileURLToPath(new URL('..', import.meta.url)), values.suite, values.reason ?? '');
    console.log(`已更新 ${count} 个案例的基线链接；请审阅清单差异与更新理由后提交。`);
  } catch (error) {
    console.error(error instanceof Error ? error.message : error);
    process.exitCode = 1;
  }
}
