/**
 * 悬浮任务中心逻辑
 * - 通过 preload 暴露的 window.livingAgentAPI 调用主进程
 * - 折叠态仅显示待接取数；展开态显示任务列表 + 一键接取
 */
(function () {
  'use strict';

  var api = window.livingAgentAPI;
  var countEl = document.getElementById('fb-count');
  var dotEl = document.getElementById('fb-dot');
  var statusEl = document.getElementById('fb-status');
  var listEl = document.getElementById('fb-list');
  var bodyEl = document.getElementById('fb-body');
  var refreshBtn = document.getElementById('fb-refresh');
  var collapseBtn = document.getElementById('fb-collapse');
  var closeBtn = document.getElementById('fb-close');

  var tasks = [];
  var hasToken = false;
  var expanded = true;
  var claimingId = null;

  var DIFFICULTY = {
    BEGINNER: { text: '入门', color: '#52c41a' },
    INTERMEDIATE: { text: '中级', color: '#1890ff' },
    ADVANCED: { text: '高级', color: '#fa8c16' },
    EXPERT: { text: '专家', color: '#f5222d' },
    MASTER: { text: '大师', color: '#722ed1' }
  };

  function diffLabel(d) {
    return DIFFICULTY[d] || { text: d || '未知', color: '#999' };
  }

  function priorityLabel(p) {
    if (p >= 5) return { text: '紧急', color: '#f5222d' };
    if (p >= 3) return { text: '高', color: '#fa8c16' };
    if (p >= 2) return { text: '中', color: '#1890ff' };
    return { text: '低', color: '#52c41a' };
  }

  function setCount(n) {
    countEl.textContent = String(n || 0);
    dotEl.className = 'fb-dot' + (n > 0 ? ' on' : '');
  }

  function setStatus(text) {
    statusEl.textContent = text || '';
    statusEl.style.display = text ? 'block' : 'none';
  }

  function render() {
    listEl.innerHTML = '';
    if (!expanded) return;
    if (!tasks.length) {
      setStatus('暂无公共任务');
      return;
    }
    setStatus('');
    tasks.forEach(function (task) {
      var li = document.createElement('li');
      li.className = 'fb-task';
      var diff = diffLabel(task.difficulty);
      var pri = priorityLabel(task.priority);

      var head = document.createElement('div');
      head.className = 'fb-task-head';
      var bDiff = document.createElement('span');
      bDiff.className = 'fb-badge';
      bDiff.style.color = diff.color;
      bDiff.style.background = diff.color + '20';
      bDiff.textContent = diff.text;
      var bPri = document.createElement('span');
      bPri.className = 'fb-badge';
      bPri.style.color = pri.color;
      bPri.style.background = pri.color + '20';
      bPri.textContent = pri.text;
      head.appendChild(bDiff);
      head.appendChild(bPri);

      var body = document.createElement('div');
      body.className = 'fb-task-body';
      var type = document.createElement('div');
      type.className = 'fb-task-type';
      type.textContent = task.taskType || '';
      var desc = document.createElement('div');
      desc.className = 'fb-task-desc';
      desc.textContent = task.description || '';
      var meta = document.createElement('div');
      meta.className = 'fb-task-meta';
      meta.textContent =
        (task.department ? task.department + ' · ' : '') +
        (task.estimatedHours != null ? task.estimatedHours + 'h' : '');

      body.appendChild(type);
      body.appendChild(desc);
      body.appendChild(meta);

      var foot = document.createElement('div');
      foot.className = 'fb-task-foot';
      var reward = document.createElement('span');
      reward.className = 'fb-reward';
      reward.textContent = (task.reward != null ? task.reward : 0) + ' 积分';
      var claimBtn = document.createElement('button');
      claimBtn.className = 'fb-claim';
      claimBtn.type = 'button';
      claimBtn.textContent = claimingId === task.taskId ? '接取中…' : (hasToken ? '接取' : '未登录');
      claimBtn.disabled = !hasToken || claimingId === task.taskId;
      claimBtn.dataset.taskId = task.taskId || '';
      claimBtn.addEventListener('click', function () {
        void handleClaim(task);
      });
      foot.appendChild(reward);
      foot.appendChild(claimBtn);

      li.appendChild(head);
      li.appendChild(body);
      li.appendChild(foot);
      listEl.appendChild(li);
    });
  }

  async function loadTasks() {
    setStatus('加载中…');
    try {
      var list = await api.taskBoard.list();
      tasks = Array.isArray(list) ? list : [];
      setCount(tasks.length);
      setStatus(tasks.length ? '' : '暂无公共任务');
      render();
    } catch (e) {
      tasks = [];
      setCount(0);
      setStatus('加载失败：' + String(e));
      render();
    }
  }

  async function refreshAuth() {
    try {
      var token = await api.auth.getToken();
      hasToken = !!token;
    } catch (e) {
      hasToken = false;
    }
    render();
  }

  async function handleClaim(task) {
    if (!hasToken) {
      setStatus('请先在主窗口登录');
      return;
    }
    claimingId = task.taskId;
    render();
    try {
      await api.taskBoard.claim(task.taskId);
      tasks = tasks.filter(function (t) {
        return t.taskId !== task.taskId;
      });
      setCount(tasks.length);
      setStatus('已接取：' + (task.taskType || ''));
    } catch (e) {
      setStatus('接取失败：' + String(e));
    } finally {
      claimingId = null;
      render();
    }
  }

  function setExpanded(v) {
    expanded = v;
    bodyEl.style.display = expanded ? 'block' : 'none';
    collapseBtn.textContent = expanded ? '▭' : '▢';
    void api.floating.setExpanded(expanded);
    if (expanded) {
      void loadTasks();
    }
  }

  refreshBtn.addEventListener('click', function () {
    void loadTasks();
  });
  collapseBtn.addEventListener('click', function () {
    setExpanded(!expanded);
  });
  closeBtn.addEventListener('click', function () {
    void api.floating.hide();
  });

  // 主进程推送的任务数变化 / 新任务
  api.onTaskBoardCountChanged(function (info) {
    setCount((info && info.count) || 0);
    if (expanded) {
      void loadTasks();
    }
  });
  api.onNewTask(function () {
    if (expanded) {
      void loadTasks();
    }
  });

  void refreshAuth();
  void loadTasks();
})();
