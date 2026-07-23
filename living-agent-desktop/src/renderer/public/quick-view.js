/**
 * P7 悬浮 Quick View 客户端逻辑
 * - 消息发送：IPC quickview:send → 主进程 → 主窗口 OfficeChatPage
 * - AI 响应：主窗口 → 主进程 → IPC quickview:response → 更新消息
 * - 部门同步：IPC quickview:set-department
 * - 选中文本：IPC quickview:set-selection
 * - 主动服务通知：IPC quickview:proactive-notification
 * - Trace 进度：随 quickview:response 一起推送
 */
const api = window.livingAgentAPI;

// ============ 状态 ============
const MAX_MESSAGES = 5; // 精简版：最多显示 5 条
let messages = [];
let isWaiting = false;
let currentDepartment = 'tech';
let isDepartmentLocked = false;
let pendingAttachments = [];
let notifications = [];

// ============ DOM 引用 ============
const $ = (id) => document.getElementById(id);
const qvMessages = $('qv-messages');
const qvEmpty = $('qv-empty');
const qvInput = $('qv-input');
const qvSend = $('qv-send');
const qvDept = $('qv-dept');
const qvLock = $('qv-lock');
const qvClose = $('qv-close');
const qvTrace = $('qv-trace');
const qvScreenshotBtn = $('qv-screenshot-btn');
const qvAttachments = $('qv-attachments');
const qvNotifDot = $('qv-notif-dot');
const qvNotifBtn = $('qv-notif-btn');
const qvNotifPanel = $('qv-notification-panel');

// ============ 渲染消息列表 ============
function renderMessages() {
  // 清空
  qvMessages.innerHTML = '';
  if (messages.length === 0 && !isWaiting) {
    qvMessages.innerHTML = '<div class="qv-empty">按 Alt+Space 唤起 · 输入问题快速提问</div>';
    return;
  }

  // 渲染消息（最多 5 条）
  const visible = messages.slice(-MAX_MESSAGES);
  for (const msg of visible) {
    const div = document.createElement('div');
    div.className = `qv-msg ${msg.isSelf ? 'user' : 'assistant'}`;
    div.innerHTML = renderContent(msg.content);
    qvMessages.appendChild(div);
  }

  // 等待指示
  if (isWaiting) {
    const waitDiv = document.createElement('div');
    waitDiv.className = 'qv-waiting';
    waitDiv.innerHTML = '<span>AI 思考中</span><span class="qv-waiting-dots"></span>';
    qvMessages.appendChild(waitDiv);
  }

  // 自动滚到底部
  qvMessages.scrollTop = qvMessages.scrollHeight;
}

// 简易 Markdown 渲染（代码块 + 行内代码 + 粗体）
function renderContent(text) {
  if (!text) return '';
  let html = escapeHtml(text);
  // 代码块
  html = html.replace(/```(\w*)\n([\s\S]*?)```/g, '<pre><code>$2</code></pre>');
  // 行内代码
  html = html.replace(/`([^`]+)`/g, '<code>$1</code>');
  // 粗体
  html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
  // 换行
  html = html.replace(/\n/g, '<br/>');
  return html;
}

function escapeHtml(text) {
  const map = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' };
  return text.replace(/[&<>"']/g, c => map[c]);
}

// ============ 渲染附件 ============
function renderAttachments() {
  qvAttachments.innerHTML = '';
  for (let i = 0; i < pendingAttachments.length; i++) {
    const att = pendingAttachments[i];
    const chip = document.createElement('span');
    chip.className = 'qv-attachment-chip';
    chip.innerHTML = `${escapeHtml(att.name)} <span class="qv-attachment-remove" data-idx="${i}">✕</span>`;
    qvAttachments.appendChild(chip);
  }
  // 绑定删除事件
  qvAttachments.querySelectorAll('.qv-attachment-remove').forEach(el => {
    el.addEventListener('click', () => {
      const idx = parseInt(el.dataset.idx);
      pendingAttachments.splice(idx, 1);
      renderAttachments();
    });
  });
}

// ============ 发送消息 ============
function sendMessage() {
  const content = qvInput.value.trim();
  if (!content && pendingAttachments.length === 0) return;
  if (isWaiting) return;

  // 添加用户消息
  messages.push({
    content,
    isSelf: true,
    timestamp: new Date().toISOString()
  });

  // 通知主进程转发到主窗口
  api.quickView.send({
    content,
    attachments: pendingAttachments.length > 0 ? pendingAttachments : undefined,
    metadata: {
      source: 'quickview',
      clientTimestamp: Date.now()
    }
  });

  // 通知主进程设置输入状态
  api.quickView.setTyping(false);

  qvInput.value = '';
  pendingAttachments = [];
  renderAttachments();
  isWaiting = true;
  qvSend.disabled = true;
  renderMessages();
}

// ============ 事件绑定 ============

// 发送按钮
qvSend.addEventListener('click', sendMessage);

// 输入框
qvInput.addEventListener('input', () => {
  qvSend.disabled = !qvInput.value.trim() && pendingAttachments.length === 0;
  // 通知主进程输入状态（防止 blur 时自动隐藏）
  api.quickView.setTyping(qvInput.value.length > 0);
});

qvInput.addEventListener('keydown', (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    sendMessage();
  }
});

// 聚焦输入框
qvInput.addEventListener('focus', () => {
  api.quickView.setTyping(true);
});

qvInput.addEventListener('blur', () => {
  // 延迟设置，避免发送按钮点击时 blur 导致问题
  setTimeout(() => {
    if (document.activeElement !== qvInput && document.activeElement !== qvSend) {
      api.quickView.setTyping(false);
    }
  }, 200);
});

// 关闭按钮
qvClose.addEventListener('click', () => {
  api.quickView.hide();
});

// 截图按钮
qvScreenshotBtn.addEventListener('click', () => {
  api.quickView.triggerScreenshot();
});

// 部门选择
qvDept.addEventListener('change', () => {
  if (!isDepartmentLocked) {
    currentDepartment = qvDept.value;
    // 通知主进程切换部门
    api.quickView.switchDepartment(currentDepartment);
  }
});

// 通知按钮
qvNotifBtn.addEventListener('click', () => {
  qvNotifPanel.classList.toggle('visible');
});

// 点击通知面板外部关闭
document.addEventListener('click', (e) => {
  if (!qvNotifPanel.contains(e.target) && e.target !== qvNotifBtn) {
    qvNotifPanel.classList.remove('visible');
  }
});

// P3: 粘贴识别（图片/文件）
qvInput.addEventListener('paste', (e) => {
  const clipboardData = e.clipboardData;
  if (!clipboardData) return;

  for (const item of clipboardData.items) {
    if (item.type.startsWith('image/')) {
      e.preventDefault();
      const blob = item.getAsFile();
      if (blob) {
        pendingAttachments.push({
          type: 'image',
          name: blob.name || 'paste-image.png',
          size: blob.size
        });
        renderAttachments();
        qvSend.disabled = false;
      }
    }
  }
});

// ============ IPC 事件监听 ============

// AI 响应
api.on('quickview:response', (data) => {
  isWaiting = false;
  qvSend.disabled = false;

  messages.push({
    content: data.content,
    isSelf: false,
    timestamp: new Date().toISOString()
  });

  renderMessages();

  // 更新 Trace
  if (data.trace && data.trace.length > 0) {
    renderTrace(data.trace);
  } else {
    qvTrace.classList.remove('visible');
    qvTrace.innerHTML = '';
  }
});

// 选中文本
api.on('quickview:set-selection', (data) => {
  if (data.text) {
    qvInput.value = data.text;
    qvInput.focus();
    qvSend.disabled = false;
  }
});

// 部门状态同步
api.on('quickview:set-department', (data) => {
  currentDepartment = data.department;
  isDepartmentLocked = data.locked;
  qvDept.value = data.department;
  qvDept.disabled = data.locked;
  qvLock.style.display = data.locked ? 'inline' : 'none';
});

// 主动服务通知
api.on('quickview:proactive-notification', (data) => {
  notifications.unshift(data);
  if (notifications.length > 10) notifications.pop();
  qvNotifDot.classList.add('visible');
  renderNotifications();
});

// Trace 更新（单独推送，不伴随 response）
api.on('quickview:trace-update', (data) => {
  if (data.trace && data.trace.length > 0) {
    renderTrace(data.trace);
  }
});

// ============ 渲染 Trace ============
function renderTrace(trace) {
  qvTrace.classList.add('visible');
  qvTrace.innerHTML = '';

  // 仅显示当前阶段名称 + 进度点
  const stepNames = {
    intake_classified: '意图识别',
    main_brain_planned: '主脑规划',
    brain_routed: '路由决策',
    department_plan_created: '部门计划',
    employee_assigned: '员工分派',
    employee_execution_started: '开始执行',
    employee_execution_completed: '执行完成',
    result_aggregated: '结果汇总'
  };

  for (const step of trace) {
    const stepEl = document.createElement('span');
    stepEl.className = 'qv-trace-step';

    const dot = document.createElement('span');
    dot.className = 'qv-trace-dot';
    if (step.status === 'done') dot.classList.add('done');
    else if (step.status === 'running') dot.classList.add('running');
    else if (step.status === 'failed') dot.classList.add('failed');

    const label = document.createElement('span');
    label.textContent = stepNames[step.stage] || step.stage;

    stepEl.appendChild(dot);
    stepEl.appendChild(label);
    qvTrace.appendChild(stepEl);
  }

  // 详情链接
  const detail = document.createElement('span');
  detail.className = 'qv-trace-detail';
  detail.textContent = '详情';
  detail.addEventListener('click', () => {
    // 跳转到主窗口 TraceVisualizer
    api.quickView.openInMainWindow();
  });
  qvTrace.appendChild(detail);
}

// ============ 渲染通知 ============
function renderNotifications() {
  qvNotifPanel.innerHTML = '';
  for (const notif of notifications) {
    const item = document.createElement('div');
    item.className = 'qv-notif-item';
    item.innerHTML = `
      <div class="qv-notif-type">${escapeHtml(notif.type || '通知')}</div>
      <div class="qv-notif-title">${escapeHtml(notif.title || '')}</div>
      <div class="qv-notif-body">${escapeHtml(notif.body || '')}</div>
    `;
    item.addEventListener('click', () => {
      qvNotifPanel.classList.remove('visible');
      qvNotifDot.classList.remove('visible');
      // 跳转到主窗口
      api.quickView.openInMainWindow();
    });
    qvNotifPanel.appendChild(item);
  }
  if (notifications.length === 0) {
    qvNotifPanel.innerHTML = '<div style="padding:10px;color:#666;text-align:center;">暂无通知</div>';
  }
}

// ============ 初始化 ============
renderMessages();
