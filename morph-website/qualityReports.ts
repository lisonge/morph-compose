import { cp, mkdir, readFile, readdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import type { Plugin } from 'vite';

const reportRoot = fileURLToPath(new URL('../morph-playground/build/reports/morph-visual/', import.meta.url));
const suites = ['quick', 'wide'];

async function status() {
  return Promise.all(suites.map(async (suite) => {
    try {
      const summary = JSON.parse(await readFile(path.join(reportRoot, suite, 'summary.json'), 'utf8'));
      if (summary.state === 'complete') await readFile(path.join(reportRoot, suite, 'index.html'));
      let publicImages = false;
      try {
        const links = JSON.parse(await readFile(path.join(reportRoot, suite, 'public-images.json'), 'utf8'));
        const html = await readFile(path.join(reportRoot, suite, 'index.html'));
        await readFile(path.join(reportRoot, suite, 'public-index.html'));
        publicImages = links.generatedAt === summary.generatedAt &&
          links.sourceSha256 === createHash('sha256').update(html).digest('hex');
      } catch { /* Local image report remains available before publishing. */ }
      return { ...summary, suite, publicImages, available: summary.state === 'complete' };
    } catch {
      return { suite, available: false, state: 'missing' };
    }
  }));
}

/** Dev reads live Desktop outputs; production contains an immutable copy of that run. */
export function qualityReports(): Plugin {
  let outDir: string;
  let building = false;
  return {
    name: 'morph-quality-reports',
    configResolved(config) {
      outDir = path.resolve(config.root, config.build.outDir);
      building = config.command === 'build';
    },
    configureServer(server) {
      server.middlewares.use(async (req, res, next) => {
        const url = new URL(req.url ?? '/', 'http://localhost');
        if (url.pathname === '/quality-status.json') {
          res.setHeader('Content-Type', 'application/json; charset=utf-8');
          res.setHeader('Cache-Control', 'no-store');
          res.end(JSON.stringify(await status()));
          return;
        }
        if (!url.pathname.startsWith('/quality-reports/')) return next();
        try {
          const relative = decodeURIComponent(url.pathname.slice('/quality-reports/'.length));
          const file = path.resolve(reportRoot, relative.endsWith('/') ? relative + 'index.html' : relative);
          if (!file.startsWith(reportRoot) || !suites.includes(relative.split('/')[0]!)) {
            res.statusCode = 404; res.end(); return;
          }
          const data = await readFile(file);
          res.setHeader('Content-Type', file.endsWith('.html') ? 'text/html; charset=utf-8'
            : file.endsWith('.png') ? 'image/png' : 'text/plain; charset=utf-8');
          res.setHeader('Cache-Control', 'no-store');
          res.end(data);
        } catch { res.statusCode = 404; res.end('Report not generated.'); }
      });
    },
    async closeBundle() {
      if (!building) return;
      const reports = await status();
      if (process.env.MORPH_REQUIRE_REPORT === '1' && !reports.some(
        (r) => r.suite === 'quick' && r.available && r.mode === 'compare' && r.passed === true,
      )) throw new Error('Deployment requires a completed, passing quick compare report. Run visualRegression first.');
      await mkdir(outDir, { recursive: true });
      await writeFile(path.join(outDir, 'quality-status.json'), JSON.stringify(reports));
      for (const report of reports.filter((r) => r.available)) {
        const source = path.join(reportRoot, report.suite);
        const destination = path.join(outDir, 'quality-reports', report.suite);
        await mkdir(destination, { recursive: true });
        // Include only report assets, never an arbitrary build directory.
        for (const name of await readdir(source)) {
          if (['index.html', 'summary.json', 'metrics.tsv', 'candidate', 'before', 'diff', 'animation', 'public-index.html', 'public-images.json'].includes(name)) {
            if (report.publicImages && ['candidate', 'before', 'diff', 'animation'].includes(name)) continue;
            if (!report.publicImages && name.startsWith('public-')) continue;
            await cp(path.join(source, report.publicImages && name === 'index.html' ? 'public-index.html' : name),
              path.join(destination, name), { recursive: true });
          }
        }
      }
    },
  };
}
