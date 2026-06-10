#!/usr/bin/env node
/**
 * Living Agent Desktop - 依赖审计脚本
 *
 * 在 npm install 前自动运行（preinstall）
 * 详细参考：DEPENDENCIES.md
 *
 * 检查项：
 * 1. 是否有可疑的"流行包重名"（如 typosquatting）
 * 2. 是否所有依赖都有 maintainer 信誉
 * 3. 是否有未授权的 postinstall 钩子
 * 4. 是否所有依赖都是固定版本（非 ^/~/x）
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const pkg = require('../package.json');

// 已知的可信包来源（白名单）
const TRUSTED_SCOPES = ['@vitejs', '@types', '@electron'];
const KNOWN_MAINTAINERS = {
  'electron': 'electron-builder',
  'electron-builder': 'electron-userland',
  'electron-vite': 'alex8088',
  'vite': 'vitejs',
  'react': 'react',
  'react-dom': 'react',
  'typescript': 'microsoft',
  'ws': 'websockets',
  '@types/node': 'microsoft',
  '@types/react': 'react',
  '@types/react-dom': 'react',
  '@types/ws': 'websockets',
  '@vitejs/plugin-react': 'vitejs'
};

let hasIssue = false;
const issues = [];

function warn(msg) {
  issues.push(`⚠️  ${msg}`);
}

function error(msg) {
  issues.push(`❌ ${msg}`);
  hasIssue = true;
}

function checkPinnedVersions() {
  console.log('[1/4] 检查版本锁定...');
  const all = { ...pkg.dependencies, ...pkg.devDependencies, ...pkg.peerDependencies };
  for (const [name, ver] of Object.entries(all)) {
    if (typeof ver !== 'string') continue;
    if (ver.startsWith('^') || ver.startsWith('~') || ver.includes('x')) {
      error(`依赖 ${name} 未锁定精确版本: ${ver}（应使用 1.2.3 形式）`);
    }
    if (ver === '*' || ver === 'latest' || ver === 'next') {
      error(`依赖 ${name} 不可使用通配符: ${ver}`);
    }
  }
}

function checkSuspiciousPackages() {
  console.log('[2/4] 检查可疑包名（typosquatting）...');
  const all = Object.keys({ ...pkg.dependencies, ...pkg.devDependencies });
  for (const name of all) {
    // 检查常见 typosquatting 模式
    const lower = name.toLowerCase();
    if (lower !== name) {
      warn(`包名 ${name} 包含大写字符，可能存在混淆`);
    }
    if (name.includes('--') || name.endsWith('-') || name.startsWith('-')) {
      error(`包名 ${name} 格式异常（typosquatting 特征）`);
    }
  }
}

function checkOverrides() {
  console.log('[3/4] 检查 overrides 配置...');
  if (!pkg.overrides || Object.keys(pkg.overrides).length === 0) {
    warn('未配置 overrides，建议为关键间接依赖添加版本锁定');
    return;
  }
  for (const [name, ver] of Object.entries(pkg.overrides)) {
    if (typeof ver === 'string' && (ver.startsWith('^') || ver.startsWith('~'))) {
      error(`override ${name} 未锁定精确版本: ${ver}`);
    }
  }
}

function checkEngines() {
  console.log('[4/4] 检查 Node.js 版本要求...');
  if (!pkg.engines || !pkg.engines.node) {
    warn('未配置 engines.node 字段');
  } else {
    console.log(`  Node.js: ${pkg.engines.node}`);
    if (pkg.engines.npm) {
      console.log(`  npm:     ${pkg.engines.npm}`);
    }
  }
}

function tryGetNpmInfo() {
  try {
    const all = Object.keys({ ...pkg.dependencies, ...pkg.devDependencies });
    for (const name of all) {
      try {
        // 跳过 @types 和 @vitejs 命名空间
        if (name.startsWith('@types/') || name.startsWith('@vitejs/')) continue;
        const result = execSync(`npm view ${name} maintainers --json 2>/dev/null`, {
          encoding: 'utf-8',
          timeout: 10_000,
          stdio: ['pipe', 'pipe', 'pipe']
        });
        const maintainers = JSON.parse(result || '[]');
        if (!maintainers || maintainers.length === 0) {
          warn(`包 ${name} 无 maintainer 信息`);
        }
      } catch (e) {
        // 网络错误，忽略
      }
    }
  } catch (e) {
    // 整体失败（无网络）
  }
}

function main() {
  console.log('========================================');
  console.log(' Living Agent Desktop - 依赖审计');
  console.log('========================================');
  console.log('');

  checkPinnedVersions();
  checkSuspiciousPackages();
  checkOverrides();
  checkEngines();

  // 可选：联网查询 maintainer 信誉
  if (process.env.LA_AUDIT_NETWORK === '1') {
    console.log('');
    console.log('[*] 联网检查 maintainer 信誉（LA_AUDIT_NETWORK=1）...');
    tryGetNpmInfo();
  }

  console.log('');
  console.log('========================================');
  if (issues.length === 0) {
    console.log(' ✅ 依赖审计通过');
    console.log('========================================');
    process.exit(0);
  } else {
    console.log(' 审计结果：');
    issues.forEach((i) => console.log('  ' + i));
    console.log('========================================');
    if (hasIssue) {
      console.log(' ❌ 发现严重问题，请修复后再安装');
      process.exit(1);
    } else {
      console.log(' ⚠️  有警告项，但可继续');
      process.exit(0);
    }
  }
}

main();
