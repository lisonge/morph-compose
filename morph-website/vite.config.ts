import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  plugins: [tailwindcss(), vue()],
  server: {
    host: '127.0.0.1',
    port: 8421,
  },
});
