/// <reference types="vite/client" />

/**
 * Vite 客户端类型
 * - 启用 *.css 等副作用导入的类型声明
 * - 启用环境变量 import.meta.env
 */
declare module '*.css';
declare module '*.svg' {
  const content: string;
  export default content;
}
declare module '*.png' {
  const content: string;
  export default content;
}
declare module '*.jpg' {
  const content: string;
  export default content;
}
declare module '*.jpeg' {
  const content: string;
  export default content;
}
declare module '*.gif' {
  const content: string;
  export default content;
}

// 标记为模块文件，避免污染全局声明
export {};
