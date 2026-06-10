import { defineConfig, externalizeDepsPlugin } from 'electron-vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';

/**
 * Electron + Vite 配置
 * - 桌面端是独立安装包，渲染层入口在 desktop/src/renderer 下
 * - 不复用 web 端 frontend/：两者部署在不同物理机器上
 * - 渲染层与 web 端共享同一后端服务（通过 HTTP API 通信）
 */
export default defineConfig({
  main: {
    plugins: [externalizeDepsPlugin()],
    build: {
      outDir: 'dist/main',
      rollupOptions: {
        input: resolve(__dirname, 'src/main/index.ts'),
        output: {
          // 拆 chunk 避免单文件过大
          manualChunks: undefined
        }
      },
      // 主进程不压缩，便于调试
      minify: false
    },
    resolve: {
      alias: {
        '@shared': resolve(__dirname, 'src/shared')
      }
    }
  },
  preload: {
    plugins: [externalizeDepsPlugin()],
    build: {
      outDir: 'dist/preload',
      rollupOptions: {
        input: resolve(__dirname, 'src/preload/index.ts')
      },
      minify: false
    }
  },
  renderer: {
    // 桌面端独立渲染层根目录（与 web 端 frontend/ 解耦）
    root: resolve(__dirname, 'src/renderer'),
    plugins: [react()],
    build: {
      outDir: resolve(__dirname, 'dist/renderer'),
      emptyOutDir: true,
      rollupOptions: {
        input: resolve(__dirname, 'src/renderer/index.html')
      }
    },
    resolve: {
      alias: {
        '@shared': resolve(__dirname, 'src/shared')
      }
    },
    server: {
      port: 5174
    }
  }
});
