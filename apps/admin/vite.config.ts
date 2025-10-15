import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      // Development: Use SOURCE files for HMR (hot module replacement)
      // Production build: Will use built dist files from node_modules
      '@eaf/product-widget-demo-ui': resolve(__dirname, '../../products/widget-demo/ui-module/src/index.ts'),
      '@axians/eaf-admin-shell': resolve(__dirname, '../../framework/admin-shell/src/index.ts'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
  optimizeDeps: {
    // Force Vite to pre-bundle these dependencies
    include: [
      'react',
      'react-dom',
      'react-admin',
      '@mui/material',
      '@mui/icons-material',
    ],
  },
});
