import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const mode = process.argv[2];
if (process.argv.length > 3 || (mode !== undefined && mode !== 'publish')) {
  console.error('用法：node scripts/quality-report.ts [publish]');
  process.exit(1);
}
const windows = process.platform === 'win32';
const task = mode === 'publish'
  ? ':morph-playground:publishVisualReport'
  : ':morph-playground:visualRegression';
// Windows 批处理必须通过命令解释器运行，参数仅来自固定任务名。
const result = spawnSync(
  windows ? (process.env.ComSpec ?? 'cmd.exe') : './gradlew',
  windows ? ['/d', '/s', '/c', `gradlew.bat ${task} --console=plain`] : [task, '--console=plain'],
  { cwd: fileURLToPath(new URL('..', import.meta.url)), stdio: 'inherit' },
);
if (result.error) console.error(result.error.message);
process.exit(result.status ?? 1);
