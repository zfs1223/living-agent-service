/**
 * P13: 敏感字段脱敏工具
 *
 * 自动识别并脱敏手机号、身份证、银行卡、邮箱、金额等敏感信息
 */

/**
 * 敏感类型枚举
 */
export type SensitiveType = 
  | 'phone'      // 手机号
  | 'idcard'     // 身份证号
  | 'bankcard'   // 银行卡号
  | 'email'      // 邮箱
  | 'amount'     // 金额
  | 'name';      // 姓名

/**
 * 敏感信息匹配规则
 */
const SENSITIVE_PATTERNS: Record<SensitiveType, RegExp> = {
  // 手机号：1开头，11位
  phone: /1[3-9]\d{9}/g,
  // 身份证号：18位，最后可能是X
  idcard: /[1-9]\d{5}(?:19|20)\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\d|3[01])\d{3}[\dXx]/g,
  // 银行卡号：16-19位
  bankcard: /(?:\d{4}[\s-]?){3,4}\d{4}/g,
  // 邮箱
  email: /[\w.-]+@[\w.-]+\.\w+/g,
  // 金额：数字+单位
  amount: /(?:￥|¥|RMB|美元|USD|欧元|EUR)?\s*\d+(?:[.,]\d{1,2})?\s*(?:元|万|亿|美元|欧元)?/g,
  // 姓名：2-4个汉字（简单匹配）
  name: /[\u4e00-\u9fa5]{2,4}/g
};

/**
 * 脱敏函数映射
 */
const MASK_FUNCTIONS: Record<SensitiveType, (match: string) => string> = {
  // 手机号：138****1234
  phone: (match) => match.slice(0, 3) + '****' + match.slice(-4),
  
  // 身份证：320***********1234
  idcard: (match) => match.slice(0, 3) + '***********' + match.slice(-4),
  
  // 银行卡：6222 **** **** 1234
  bankcard: (match) => {
    const digits = match.replace(/[\s-]/g, '');
    return digits.slice(0, 4) + ' **** **** ' + digits.slice(-4);
  },
  
  // 邮箱：a***@example.com
  email: (match) => {
    const [local, domain] = match.split('@');
    return local.slice(0, 1) + '***@' + domain;
  },
  
  // 金额：保留数字，单位显示为*
  amount: (match) => {
    return match.replace(/(￥|¥|RMB|美元|USD|欧元|EUR|元|万|亿)/g, '*');
  },
  
  // 姓名：张**
  name: (match) => match.slice(0, 1) + '**'
};

/**
 * 敏感信息识别结果
 */
export interface SensitiveMatch {
  type: SensitiveType;
  value: string;
  masked: string;
  start: number;
  end: number;
}

/**
 * 识别文本中的敏感信息
 */
export function detectSensitives(text: string): SensitiveMatch[] {
  const matches: SensitiveMatch[] = [];
  
  for (const [type, pattern] of Object.entries(SENSITIVE_PATTERNS)) {
    const regex = new RegExp(pattern.source, pattern.flags);
    let match;
    
    while ((match = regex.exec(text)) !== null) {
      const value = match[0];
      const masked = MASK_FUNCTIONS[type as SensitiveType](value);
      
      matches.push({
        type: type as SensitiveType,
        value,
        masked,
        start: match.index,
        end: match.index + value.length
      });
    }
  }
  
  // 按位置排序
  matches.sort((a, b) => a.start - b.start);
  
  return matches;
}

/**
 * 对文本进行脱敏处理
 */
export function maskSensitives(text: string): string {
  const matches = detectSensitives(text);
  
  if (matches.length === 0) return text;
  
  // 从后往前替换，避免位置偏移
  let result = text;
  for (let i = matches.length - 1; i >= 0; i--) {
    const match = matches[i];
    result = result.slice(0, match.start) + match.masked + result.slice(match.end);
  }
  
  return result;
}

/**
 * 高亮敏感信息（用于显示）
 */
export function highlightSensitives(text: string): Array<{ text: string; isSensitive: boolean; type?: SensitiveType; original?: string }> {
  const matches = detectSensitives(text);
  
  if (matches.length === 0) {
    return [{ text, isSensitive: false }];
  }
  
  const result: Array<{ text: string; isSensitive: boolean; type?: SensitiveType; original?: string }> = [];
  let lastEnd = 0;
  
  for (const match of matches) {
    // 添加前面的普通文本
    if (match.start > lastEnd) {
      result.push({
        text: text.slice(lastEnd, match.start),
        isSensitive: false
      });
    }
    
    // 添加敏感信息
    result.push({
      text: match.masked,
      isSensitive: true,
      type: match.type,
      original: match.value
    });
    
    lastEnd = match.end;
  }
  
  // 添加最后的普通文本
  if (lastEnd < text.length) {
    result.push({
      text: text.slice(lastEnd),
      isSensitive: false
    });
  }
  
  return result;
}

/**
 * 检查用户是否有权限查看完整敏感信息
 * 权限规则：FULL 或 DEPARTMENT 级别
 */
export function canViewSensitive(accessLevel?: string): boolean {
  return accessLevel === 'FULL' || accessLevel === 'DEPARTMENT';
}