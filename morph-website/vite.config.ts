import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import tailwindcss from '@tailwindcss/vite';
import { qualityReports } from './qualityReports.ts';

export default defineConfig(({ mode }) => ({
  plugins: [tailwindcss(), vue(), qualityReports()],
  optimizeDeps: mode === 'quality' ? { entries: ['quality.html'] } : undefined,
  build: {
    rollupOptions: { input: mode === 'quality' ? ['quality.html'] : ['index.html', 'quality.html'] },
  },
  server: {
    host: '127.0.0.1',
    port: 8421,
  },
}));
