/**
 * window.livingAgentAPI 类型声明
 * 渲染进程通过 window.livingAgentAPI 访问主进程能力
 *
 * 类型来源：src/shared/api-types.ts
 */
import type { LivingAgentAPI } from '@shared/api-types';

declare global {
  interface Window {
    livingAgentAPI: LivingAgentAPI;
  }
}

export {};
