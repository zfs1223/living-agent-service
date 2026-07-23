/**
 * P4: 富文本消息渲染组件
 * 支持：Markdown 基础语法（标题、列表、代码块）、代码高亮（行号）、表格、附件渲染
 * P13: 敏感信息脱敏（可选）
 * 轻量实现：不依赖 react-markdown，使用正则 + DOM 安全处理
 */

import { useMemo } from 'react';
import type { ChatAttachment } from './OfficeChatPage';
import { maskSensitives } from '../../utils/sensitive-mask';

interface MessageRendererProps {
  content: string;
  attachments?: ChatAttachment[];
  isSelf?: boolean;
  /** P13: 是否启用敏感信息脱敏（默认 false） */
  maskSensitive?: boolean;
}

/**
 * 转义 HTML 特殊字符，防止 XSS
 */
function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

/**
 * 处理行内代码 `code`
 */
function processInlineCode(text: string): string {
  return text.replace(/`([^`]+)`/g, '<code class="inline-code">$1</code>');
}

/**
 * 处理粗体 **text** 和斜体 *text*
 */
function processBoldAndItalic(text: string): string {
  return text
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/\*([^*]+)\*/g, '<em>$1</em>');
}

/**
 * 处理链接 [text](url)
 */
function processLinks(text: string): string {
  return text.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer" class="markdown-link">$1</a>');
}

/**
 * 处理代码块 ```lang\ncode```
 * 返回 { html: string, remaining: string }
 */
function processCodeBlocks(text: string): { html: string; remaining: string } {
  const parts: string[] = [];
  let remaining = text;
  const codeBlockRegex = /```(\w*)\n([\s\S]*?)```/g;
  let match;
  let lastIndex = 0;

  while ((match = codeBlockRegex.exec(text)) !== null) {
    // 添加代码块前的内容
    parts.push(escapeHtml(text.slice(lastIndex, match.index)));
    const lang = match[1] || 'plaintext';
    const code = match[2];
    const lines = code.split('\n');
    const numberedCode = lines
      .map((line, i) => `<span class="code-line"><span class="line-number">${i + 1}</span><span class="line-content">${escapeHtml(line)}</span></span>`)
      .join('\n');
    parts.push(`<pre class="code-block" data-lang="${lang}"><code>${numberedCode}</code></pre>`);
    lastIndex = match.index + match[0].length;
  }

  // 添加剩余内容
  parts.push(escapeHtml(text.slice(lastIndex)));
  remaining = '';

  return { html: parts.join(''), remaining };
}

/**
 * 处理表格
 * 简单实现：| col1 | col2 | 转为 table
 */
function processTables(text: string): string {
  const lines = text.split('\n');
  const result: string[] = [];
  let inTable = false;
  let tableRows: string[] = [];

  for (const line of lines) {
    const isTableRow = /^\|(.+)\|$/.test(line.trim());
    const isSeparator = /^\|[-|\s]+\|$/.test(line.trim());

    if (isTableRow && !isSeparator) {
      if (!inTable) {
        inTable = true;
        tableRows = [];
      }
      tableRows.push(line.trim());
    } else {
      if (inTable) {
        // 结束表格
        result.push(renderTable(tableRows));
        tableRows = [];
        inTable = false;
      }
      result.push(line);
    }
  }

  // 处理末尾表格
  if (inTable && tableRows.length > 0) {
    result.push(renderTable(tableRows));
  }

  return result.join('\n');
}

function renderTable(rows: string[]): string {
  if (rows.length === 0) return '';
  const htmlRows = rows.map((row, rowIdx) => {
    const cells = row.slice(1, -1).split('|').map(cell => cell.trim());
    const tag = rowIdx === 0 ? 'th' : 'td';
    const cellHtml = cells.map(cell => `<${tag}>${processInlineCode(processBoldAndItalic(escapeHtml(cell)))}</${tag}>`).join('');
    return `<tr>${cellHtml}</tr>`;
  });
  return `<table class="markdown-table"><tbody>${htmlRows.join('')}</tbody></table>`;
}

/**
 * 处理标题 # ~ ######
 */
function processHeaders(text: string): string {
  return text
    .replace(/^###### (.+)$/gm, '<h6 class="markdown-h6">$1</h6>')
    .replace(/^##### (.+)$/gm, '<h5 class="markdown-h5">$1</h5>')
    .replace(/^#### (.+)$/gm, '<h4 class="markdown-h4">$1</h4>')
    .replace(/^### (.+)$/gm, '<h3 class="markdown-h3">$1</h3>')
    .replace(/^## (.+)$/gm, '<h2 class="markdown-h2">$1</h2>')
    .replace(/^# (.+)$/gm, '<h1 class="markdown-h1">$1</h1>');
}

/**
 * 处理无序列表 - item 或 * item
 */
function processUnorderedList(text: string): string {
  const lines = text.split('\n');
  const result: string[] = [];
  let inList = false;

  for (const line of lines) {
    const match = line.match(/^[-*]\s+(.+)$/);
    if (match) {
      if (!inList) {
        result.push('<ul class="markdown-list">');
        inList = true;
      }
      result.push(`<li>${processInlineCode(processBoldAndItalic(escapeHtml(match[1])))}</li>`);
    } else {
      if (inList) {
        result.push('</ul>');
        inList = false;
      }
      result.push(line);
    }
  }

  if (inList) result.push('</ul>');
  return result.join('\n');
}

/**
 * 处理有序列表 1. item
 */
function processOrderedList(text: string): string {
  const lines = text.split('\n');
  const result: string[] = [];
  let inList = false;

  for (const line of lines) {
    const match = line.match(/^(\d+)\.\s+(.+)$/);
    if (match) {
      if (!inList) {
        result.push('<ol class="markdown-list-ordered">');
        inList = true;
      }
      result.push(`<li>${processInlineCode(processBoldAndItalic(escapeHtml(match[2])))}</li>`);
    } else {
      if (inList) {
        result.push('</ol>');
        inList = false;
      }
      result.push(line);
    }
  }

  if (inList) result.push('</ol>');
  return result.join('\n');
}

/**
 * 处理段落（连续非空行转为 <p>）
 */
function processParagraphs(text: string): string {
  const lines = text.split('\n');
  const result: string[] = [];
  let paragraph: string[] = [];

  for (const line of lines) {
    // 跳过已经是 HTML 标签的行
    if (line.startsWith('<')) {
      if (paragraph.length > 0) {
        result.push(`<p class="markdown-p">${paragraph.join(' ')}</p>`);
        paragraph = [];
      }
      result.push(line);
    } else if (line.trim() === '') {
      if (paragraph.length > 0) {
        result.push(`<p class="markdown-p">${paragraph.join(' ')}</p>`);
        paragraph = [];
      }
      result.push('');
    } else {
      paragraph.push(line);
    }
  }

  if (paragraph.length > 0) {
    result.push(`<p class="markdown-p">${paragraph.join(' ')}</p>`);
  }

  return result.join('\n');
}

/**
 * 主渲染函数：Markdown -> HTML
 */
function renderMarkdown(content: string): string {
  // 1. 先处理代码块（避免被其他处理干扰）
  const { html: codeBlockHtml, remaining: remainingAfterCode } = processCodeBlocks(content);

  // 2. 处理表格
  const tableHtml = processTables(remainingAfterCode);

  // 3. 处理标题
  const headerHtml = processHeaders(tableHtml);

  // 4. 处理列表
  const listHtml = processUnorderedList(headerHtml);
  const orderedListHtml = processOrderedList(listHtml);

  // 5. 处理行内元素（链接、粗体、斜体、行内代码）
  const inlineHtml = processLinks(orderedListHtml);
  const boldItalicHtml = processBoldAndItalic(inlineHtml);
  const inlineCodeHtml = processInlineCode(boldItalicHtml);

  // 6. 处理段落
  const finalHtml = processParagraphs(inlineCodeHtml);

  return finalHtml;
}

/**
 * 附件渲染组件
 */
function AttachmentList({ attachments }: { attachments: ChatAttachment[] }) {
  if (!attachments || attachments.length === 0) return null;

  return (
    <div className="message-attachments">
      {attachments.map(att => {
        switch (att.type) {
          case 'image':
            return (
              <div key={att.fileId} className="attachment attachment--image">
                {att.thumbnailUrl ? (
                  <img src={att.thumbnailUrl} alt={att.name} className="attachment__thumbnail" />
                ) : (
                  <div className="attachment__placeholder">
                    <span className="attachment__icon">🖼️</span>
                    <span className="attachment__name">{att.name}</span>
                  </div>
                )}
              </div>
            );
          case 'screenshot':
            return (
              <div key={att.fileId} className="attachment attachment--screenshot">
                {att.url ? (
                  <img src={att.url} alt={att.name} className="attachment__screenshot" />
                ) : (
                  <div className="attachment__placeholder">
                    <span className="attachment__icon">📷</span>
                    <span className="attachment__name">{att.name}</span>
                  </div>
                )}
              </div>
            );
          case 'file':
            return (
              <div key={att.fileId} className="attachment attachment--file">
                <span className="attachment__icon">📎</span>
                <span className="attachment__name">{att.name}</span>
                {att.size && <span className="attachment__size">{formatSize(att.size)}</span>}
              </div>
            );
          case 'audio':
            return (
              <div key={att.fileId} className="attachment attachment--audio">
                <span className="attachment__icon">🎵</span>
                <span className="attachment__name">{att.name}</span>
              </div>
            );
          default:
            return (
              <div key={att.fileId} className="attachment">
                <span className="attachment__name">{att.name}</span>
              </div>
            );
        }
      })}
    </div>
  );
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/**
 * MessageRenderer 组件
 */
export default function MessageRenderer({ content, attachments, isSelf, maskSensitive = false }: MessageRendererProps) {
  // P13: 如果启用敏感信息脱敏，先对内容进行脱敏处理
  const processedContent = useMemo(() => {
    return maskSensitive ? maskSensitives(content) : content;
  }, [content, maskSensitive]);

  const htmlContent = useMemo(() => renderMarkdown(processedContent), [processedContent]);

  return (
    <div className={`message-renderer ${isSelf ? 'message-renderer--self' : ''}`}>
      {/* 附件区域（图片/文件等） */}
      <AttachmentList attachments={attachments || []} />

      {/* Markdown 内容区域 */}
      <div
        className="message-content-markdown"
        dangerouslySetInnerHTML={{ __html: htmlContent }}
      />
    </div>
  );
}