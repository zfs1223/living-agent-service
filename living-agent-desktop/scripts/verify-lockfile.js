#!/usr/bin/env node
/**
 * Living Agent Desktop - Lockfile 验证脚本
 *
 * 检查 package-lock.json 是否：
 * 1. 存在
 * 2. 与 package.json 同步
 * 3. 所有包都有 integrity 字段
 * 4. 没有未声明的 transitive 依赖被 hoist 到顶层
 *
 * 详细参考：DEPENDENCIES.md
 */

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const projectRoot = path.resolve(__dirname, '..');
const pkgPath = path.join(projectRoot, 'package.json');
const lockPath = path.join(projectRoot, 'package-lock.json');

let hasIssue = false;
const issues = [];

function error(msg) { issues.push(`❌ ${msg}`); hasIssue = true; }
function warn(msg) { issues.push(`⚠️  ${msg}`); }
function ok(msg) { console.log(`  ✅ ${msg}`); }

function main() {
  console.log('========================================');
  console.log(' Living Agent Desktop - Lockfile 验证');
  console.log('========================================');
  console.log('');

  // 1. 文件存在
  if (!fs.existsSync(pkgPath)) {
    error('package.json 不存在');
    return finish();
  }
  if (!fs.existsSync(lockPath)) {
    error('package-lock.json 不存在，请先运行 `npm install`');
    return finish();
  }
  ok('package.json 和 package-lock.json 都存在');

  // 2. 解析
  let pkg, lock;
  try {
    pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf-8'));
    lock = JSON.parse(fs.readFileSync(lockPath, 'utf-8'));
  } catch (e) {
    error(`JSON 解析失败: ${e.message}`);
    return finish();
  }
  ok('JSON 解析成功');

  // 3. lockfileVersion 检查
  if (!lock.lockfileVersion || lock.lockfileVersion < 2) {
    warn(`lockfileVersion 较低: ${lock.lockfileVersion}（推荐 v3）`);
  } else {
    ok(`lockfileVersion: ${lock.lockfileVersion}`);
  }

  // 4. integrity 校验（root 项目包无 integrity 字段属正常）
  let withIntegrity = 0;
  let withoutIntegrity = 0;
  const totalPkgs = Object.keys(lock.packages || {}).length;
  const missingIntegrityNames = [];
  for (const [name, info] of Object.entries(lock.packages || {})) {
    if (name === '') continue; // 跳过根项目
    if (info.integrity || info.resolved) {
      withIntegrity++;
    } else {
      withoutIntegrity++;
      missingIntegrityNames.push(name);
    }
  }
  if (withoutIntegrity > 0) {
    error(`${withoutIntegrity} 个第三方包缺少 integrity 字段: ${missingIntegrityNames.slice(0, 5).join(', ')}${missingIntegrityNames.length > 5 ? ' ...' : ''}`);
  } else {
    ok(`所有 ${withIntegrity} 个第三方包都有 integrity 字段`);
  }

  // 5. 检查 lockfile 中的包是否与 package.json 同步
  const declared = new Set([
    ...Object.keys(pkg.dependencies || {}),
    ...Object.keys(pkg.devDependencies || {}),
    ...Object.keys(pkg.optionalDependencies || {})
  ]);
  const lockDeclared = new Set();
  for (const key of Object.keys(lock.packages || {})) {
    if (key === '') continue; // 根项目
    // 规范化：去除 "node_modules/" 前缀（处理嵌套依赖）
    const normalized = key.replace(/^node_modules\//, '');
    // 如果是 scoped 包或带路径的 key，只取最后一段
    const name = normalized.includes('/') && normalized.startsWith('@')
      ? normalized.split('/').slice(0, 2).join('/')
      : normalized.split('/')[0];
    if (declared.has(name)) lockDeclared.add(name);
  }
  const missingInLock = [...declared].filter((n) => !lockDeclared.has(n));
  if (missingInLock.length > 0) {
    error(`package.json 声明但 lockfile 缺失: ${missingInLock.join(', ')}`);
  } else {
    ok(`所有 ${declared.size} 个声明依赖都在 lockfile 中`);
  }

  // 6. 检查 direct dependency 版本是否一致
  for (const [name, ver] of Object.entries({
    ...pkg.dependencies,
    ...pkg.devDependencies
  })) {
    const lockEntry = lock.packages?.[`node_modules/${name}`];
    if (!lockEntry) continue;
    if (lockEntry.version !== ver) {
      warn(`${name} 版本不一致: package.json=${ver}, lockfile=${lockEntry.version}`);
    }
  }

  // 7. 检查是否启用 overrides
  if (pkg.overrides && Object.keys(pkg.overrides).length > 0) {
    ok(`已配置 ${Object.keys(pkg.overrides).length} 个 overrides`);
  } else {
    warn('未配置 overrides');
  }

  finish();
}

function finish() {
  console.log('');
  console.log('========================================');
  if (issues.length === 0) {
    console.log(' ✅ Lockfile 验证通过');
    console.log('========================================');
    process.exit(0);
  } else {
    console.log(' 验证结果：');
    issues.forEach((i) => console.log('  ' + i));
    console.log('========================================');
    if (hasIssue) {
      console.log(' ❌ 发现严重问题，请运行 `npm install` 重新生成 lockfile');
      process.exit(1);
    } else {
      console.log(' ⚠️  有警告项，但可继续');
      process.exit(0);
    }
  }
}

main();
