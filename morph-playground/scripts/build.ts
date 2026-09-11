import { spawn } from 'node:child_process';
import {
  access,
  cp,
  readFile,
  readdir,
  rename,
  rm,
  writeFile,
} from 'node:fs/promises';
import { basename, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { init, parse } from 'es-module-lexer';

type SourceMap = {
  sources?: string[];
};

const scriptDir = dirname(fileURLToPath(import.meta.url));
const packageDir = dirname(scriptDir);
const repositoryDir = dirname(packageDir);
const projectName = basename(packageDir);
const isWindows = process.platform === 'win32';
const gradleWrapper = join(
  repositoryDir,
  isWindows ? 'gradlew.bat' : 'gradlew',
);
const wasmOutputDir = join(
  packageDir,
  'build',
  'compileSync',
  'wasmJs',
  'main',
  'productionExecutable',
  'kotlin',
);
const skikoRuntimeDir = join(
  packageDir,
  'build',
  'compose',
  'skiko-runtime-processed-wasmjs',
);
const distDir = join(packageDir, 'dist');
const gradleTasks = [
  `:${projectName}:compileProductionExecutableKotlinWasmJs`,
  `:${projectName}:processSkikoRuntimeForKWasm`,
];
const skikoRuntimeFiles = ['skiko.mjs', 'skiko.wasm'];
const typeDeclarationFileName = 'morph-playground.d.mts';
const generatedModuleFileName = 'morph-playground.internal.mjs';
const publicModuleFileName = 'morph-playground.mjs';

await new Promise<void>((resolvePromise, reject) => {
  const command = isWindows
    ? (process.env.ComSpec ?? 'cmd.exe')
    : gradleWrapper;
  const args = isWindows
    ? ['/d', '/s', '/c', `""${gradleWrapper}" ${gradleTasks.join(' ')}"`]
    : gradleTasks;
  const gradle = spawn(command, args, {
    cwd: repositoryDir,
    stdio: 'inherit',
    windowsVerbatimArguments: isWindows,
  });
  gradle.on('error', reject);
  gradle.on('exit', (code) => {
    if (code === 0) resolvePromise();
    else reject(new Error(`Gradle exited with code ${code}`));
  });
});

if (dirname(distDir) !== packageDir) {
  throw new Error(`Refusing to replace a directory outside ${packageDir}`);
}

await rm(distDir, { recursive: true, force: true });
await cp(wasmOutputDir, distDir, { recursive: true });

for (const runtimeFileName of skikoRuntimeFiles) {
  await cp(
    join(skikoRuntimeDir, runtimeFileName),
    join(distDir, runtimeFileName),
  );
}

await rename(
  join(distDir, publicModuleFileName),
  join(distDir, generatedModuleFileName),
);
await writeFile(
  join(distDir, publicModuleFileName),
  `export { renderMorphPlayground } from './${generatedModuleFileName}';\n`,
  'utf8',
);

const typeDeclarationFile = join(distDir, typeDeclarationFileName);
const generatedTypeDeclarations = await readFile(typeDeclarationFile, 'utf8');
const generatedRenderSignature =
  'export declare function renderMorphPlayground(container: NonNullable<unknown>): void;';
const publicRenderSignature =
  'export declare function renderMorphPlayground(container: Element): void;';

if (!generatedTypeDeclarations.includes(generatedRenderSignature)) {
  throw new Error(
    `Unexpected renderMorphPlayground declaration in ${typeDeclarationFileName}`,
  );
}

await writeFile(
  typeDeclarationFile,
  generatedTypeDeclarations.replace(generatedRenderSignature, publicRenderSignature),
  'utf8',
);

const distFileNames = await readdir(distDir);
const moduleFileNames = distFileNames.filter((name) => name.endsWith('.mjs'));
let viteIgnoredNodeImportCount = 0;

await init;

for (const moduleFileName of moduleFileNames) {
  const moduleFile = join(distDir, moduleFileName);
  const moduleSource = await readFile(moduleFile, 'utf8');
  const [moduleImports] = parse(moduleSource, moduleFileName);
  const nodeModuleImports = moduleImports.filter(
    ({ n }) => n?.startsWith('node:') === true,
  );

  for (const { d } of nodeModuleImports) {
    if (d < 0) {
      throw new Error(`Cannot rewrite a static Node import in ${moduleFileName}`);
    }
  }

  let rewrittenSource = moduleSource;
  for (const { s } of [...nodeModuleImports].reverse()) {
    rewrittenSource = `${rewrittenSource.slice(0, s)}/* @vite-ignore */ ${rewrittenSource.slice(s)}`;
    viteIgnoredNodeImportCount += 1;
  }

  const [rewrittenImports] = parse(rewrittenSource, moduleFileName);
  const unignoredNodeImports = rewrittenImports.filter(
    ({ d, n, s }) =>
      n?.startsWith('node:') === true &&
      (d < 0 || !rewrittenSource.slice(d, s).includes('/* @vite-ignore */')),
  );
  if (unignoredNodeImports.length > 0) {
    throw new Error(`A Node import is not ignored by Vite in ${moduleFileName}`);
  }

  if (rewrittenSource !== moduleSource) {
    await writeFile(moduleFile, rewrittenSource, 'utf8');
  }
}

const mapFileNames = distFileNames.filter((name) => name.endsWith('.map'));

for (const mapFileName of mapFileNames) {
  const mapFile = join(distDir, mapFileName);
  const sourceMap = JSON.parse(await readFile(mapFile, 'utf8')) as SourceMap;

  sourceMap.sources = sourceMap.sources?.map((source) => {
    const normalizedSource = source.replaceAll('\\', '/');
    const sourceMarker = '/src/';
    const sourceIndex = normalizedSource.lastIndexOf(sourceMarker);
    if (sourceIndex === -1) return normalizedSource;
    return `../src/${normalizedSource.slice(sourceIndex + sourceMarker.length)}`;
  });

  await writeFile(mapFile, `${JSON.stringify(sourceMap)}\n`, 'utf8');
}

for (const moduleFileName of moduleFileNames) {
  const moduleFile = join(distDir, moduleFileName);
  const moduleSource = await readFile(moduleFile, 'utf8');
  const [moduleImports] = parse(moduleSource, moduleFileName);

  for (const { n } of moduleImports) {
    if (typeof n !== 'string' || !n.startsWith('.')) continue;
    const importedFile = resolve(dirname(moduleFile), n.split(/[?#]/u, 1)[0]);
    await access(importedFile).catch(() => {
      throw new Error(`${moduleFileName} imports missing file ${n}`);
    });
  }

  const assetReferences = moduleSource.matchAll(
    /new URL\(\s*(['"])([^'"]+)\1\s*,\s*import\.meta\.url\s*\)/gu,
  );
  for (const match of assetReferences) {
    const assetName = match[2];
    if (/^(?:[a-z]+:|\/)/iu.test(assetName)) continue;
    const assetFile = resolve(dirname(moduleFile), assetName);
    await access(assetFile).catch(() => {
      throw new Error(`${moduleFileName} references missing asset ${assetName}`);
    });
  }
}

console.log(`Copied Kotlin/Wasm and Skiko output to ${distDir}`);
console.log(`Marked ${viteIgnoredNodeImportCount} Node import(s) with Vite ignore comments`);
console.log(`Validated ${moduleFileNames.length} module(s) and ${mapFileNames.length} source map(s)`);
