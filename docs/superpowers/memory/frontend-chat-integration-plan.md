# 前端 P5 实施计划：多轮对话 + 记忆管理页

> 日期：2026-07-18
> 状态：计划中
> 对应 V2 计划 P5 模块（2 天）

---

## 一、技术背景

| 维度 | 选型 |
|------|------|
| 框架 | Next.js 16 + TypeScript + React 19 |
| 路由 | App Router（`src/app/`） |
| 样式 | Tailwind CSS v4（复用 `workspace-*` design system） |
| API | 原生 `fetch()` + `credentials: 'include'` |
| 用户身份 | Cookie `ai_agent_login` → `getUserInfo().userId` |
| 编程模式 | 全 `'use client'`，`useEffect` 数据获取 |
| 认证中间件 | `src/proxy.ts` 检查 `ai_agent_login` cookie，未登录跳 `/login` |
| Markdown | `MarkdownRenderer`（react-markdown + remark-gfm + rehype-katex + rehype-highlight） |
| 分页组件 | `src/components/Pagination`（`pageNo/pageSize/total/onChange`） |

### JWT 鉴权变更（重要）

V2 安全加固后，后端不再通过 `@RequestParam userId` 传用户 ID，全部从 JWT cookie 自动提取。前端无需传 `userId` 参数。

| 接口 | V1（旧） | V2（新） |
|------|---------|---------|
| `POST /create_session` | `?userId=1` | 无需参数 |
| `POST /stream` | body 含 `userId` | body 不含 `userId` |
| `POST /save` | body 含 `userId` | body 不含 `userId` |
| `GET /memory/search` | `?userId=1&q=...` | `?q=...` |
| `GET /memory/list` | `?userId=1&page=...` | `?page=...` |
| `DELETE /memory/{id}` | — | 从 JWT 中提取 userId 做权限校验 |

### 后端实际接口确认

| 接口 | 方法 | 参数 | 返回 |
|------|------|------|------|
| `/writing/chat/create_session` | POST | `?draftId`(可选) | `{ code, data: sessionId }` |
| `/writing/chat/stream` | POST | body `{ sessionId, message }` | NDJSON 流 `{"chunk":{"type":"token/done","content":"..."}}` |
| `/writing/chat/save` | POST | body `{ sessionId, message, response }` | `{ code, data: "ok" }` |
| `/memory/search` | GET | `?q=xxx&n=5` | `{ code, data: { query, items[], total } }` |
| `/memory/list` | GET | `?page=1&pageSize=20` | `{ code, data: { items[], page, pageSize, total } }` |
| `/memory/{id}` | GET | path `id` | `{ code, data: MemoryItem }` |
| `/memory/{id}` | DELETE | path `id` | `{ code, data: "ok" }` |

---

## 二、模块一：多轮对话写作面板

### 2.1 目标

在现有 `AiWritingPanel` 中新增 Tab，不新增路由页面。

```
┌─────────────────────────────────────┐
│ [  快捷操作  ]  [  对话写作  ]      │ ← Tab 切换
├─────────────────────────────────────┤
│  快捷操作 = 现有 AiWritingPanel     │
│  对话写作 = 新增 WritingChatPanel   │
└─────────────────────────────────────┘
```

### 2.2 新增/修改文件

| 文件 | 操作 | 说明 |
|------|:---:|------|
| `src/api/writing-chat.ts` | **新增** | API 层 |
| `src/types/writing-chat.ts` | **新增** | 类型定义 |
| `src/components/WritingChatPanel/index.tsx` | **新增** | 对话面板组件 |
| `src/components/AiWritingPanel/index.tsx` | **修改** | 加 Tab 切换，包裹现有内容 |
| `src/app/drafts/[draftId]/page.tsx` | **修改** | 传 `title` prop 给 AiWritingPanel |

### 2.3 `src/types/writing-chat.ts`

```ts
export interface ChatMessage {
  role: "user" | "assistant";
  content: string;
  timestamp?: number;
}

export interface StreamChunkMsg {
  chunk: {
    type: "token" | "done" | "error";
    content?: string;
  };
}
```

### 2.4 `src/api/writing-chat.ts`

```ts
import { API_CONFIG } from '@/config/api-config';
const BASE = API_CONFIG.BASE_URL;

export const writingChatApi = {
  createSession: async (draftId?: number): Promise<string> => {
    const params = draftId ? `?draftId=${draftId}` : '';
    const res = await fetch(`${BASE}/writing/chat/create_session${params}`, {
      method: 'POST', credentials: 'include',
    });
    const json = await res.json();
    if (json.code !== '0000') throw new Error(json.info || '创建会话失败');
    return json.data;
  },

  streamChat: async (
    sessionId: string, message: string,
    onToken: (text: string) => void, onDone: () => void, onError: (err: Error) => void,
  ): Promise<AbortController> => {
    const controller = new AbortController();
    const res = await fetch(`${BASE}/writing/chat/stream`, {
      method: 'POST', credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sessionId, message }),
      signal: controller.signal,
    });
    if (!res.ok) { onError(new Error(`HTTP ${res.status}`)); return controller; }

    const reader = res.body!.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    const process = async () => {
      try {
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split('\n');
          buffer = lines.pop() || '';
          for (const line of lines) {
            const trimmed = line.trim();
            if (!trimmed) continue;
            try {
              const msg = JSON.parse(trimmed);
              if (msg.chunk?.type === 'token') onToken(msg.chunk.content ?? '');
              else if (msg.chunk?.type === 'done') { if (!controller.signal.aborted) onDone(); return; }
              else if (msg.chunk?.type === 'error') { onError(new Error(msg.chunk.content || '')); return; }
            } catch { /* skip non-JSON line */ }
          }
        }
        if (buffer.trim()) {
          try { const msg = JSON.parse(buffer.trim()); if (msg.chunk?.type === 'token') onToken(msg.chunk.content ?? ''); } catch {}
        }
        if (!controller.signal.aborted) onDone();
      } catch (err: any) {
        if (err.name === 'AbortError') { onDone(); return; }
        onError(err instanceof Error ? err : new Error(String(err)));
      }
    };
    process();
    return controller;
  },

  save: async (sessionId: string, message: string, response: string): Promise<void> => {
    await fetch(`${BASE}/writing/chat/save`, {
      method: 'POST', credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sessionId, message, response }),
    });
  },
};
```

### 2.5 WritingChatPanel 组件功能清单

| 功能 | 说明 |
|------|------|
| 自动创建会话 | `useEffect` 调用 `createSession(draftId)`，含 loading/error 状态 |
| 草稿上下文 | 第一条消息自动拼接标题+正文前 500 字 |
| 对话气泡 | 用户右对齐（`bg-[#eef5f0]`），AI 左对齐（白底 `workspace-panel`） |
| AI 气泡按钮 | 每条 AI 消息底部「追加正文」「替换正文」 |
| 流式 buffer | `MarkdownRenderer` + `stream={true}` 实时渲染 |
| 输入框 | Enter 发送，Shift+Enter 换行 |
| 停止生成 | AbortController 中止 |
| 保存记忆 | 调用 save 触发后端记忆抽取 |
| 清空对话 | 重置 messages + firstMessageSent |
| 滚动 | 新消息/流式内容自动滚动到底部 |

### 2.6 AiWritingPanel 修改方案

当前 `AiWritingPanel` 的 props：
```ts
interface AiWritingPanelProps {
  draftId: number;
  content: string;
  getPromptParams: () => Record<string, unknown>;
  onApplyResult: (action: "append" | "replace" | "fillSummary", resultContent: string) => void;
}
```

**新增 props**：`title: string`（草稿标题，传给 WritingChatPanel）

**改动范围**：
1. 在现有 return 最外层加 Tab 栏
2. 用 `mode` state 控制渲染：quick = 现有全部 UI，chat = WritingChatPanel
3. 现有代码不需要重构，只是外层包裹一层条件渲染

### 2.7 流式 Markdown 渲染说明

项目已有 `MarkdownRenderer` 组件支持 `stream` prop。在流式期间传 `stream={true}`，内部会禁用部分渲染优化以确保逐 token 展示。流完后切为 `stream={false}` 走完整 Markdown 解析。

### 2.8 对话历史不持久化（V2 阶段限制）

当前方案对话历史仅存在组件 state 中，刷新/切换 Tab 会丢失。原因：
- 后端 Session 恢复功能已实现（P3），Agent 侧可以恢复上下文
- 前端 UI 恢复需要额外接口 `GET /writing/chat/history?sessionId=xxx`，后端暂未实现
- 后续迭代可追加

---

## 三、模块二：记忆管理页面 `/memory`

### 3.1 目标

独立页面，路由 `/memory`，展示用户所有长期记忆，支持搜索、删除、分页。

### 3.2 新增/修改文件

| 文件 | 操作 | 说明 |
|------|:---:|------|
| `src/types/memory.ts` | **新增** | 记忆相关类型定义 |
| `src/api/memory.ts` | **新增** | 记忆管理 API |
| `src/app/memory/page.tsx` | **新增** | 记忆管理页面 |
| `src/components/WorkspaceHeader/index.tsx` | **修改** | NAV_ITEMS + 图标 |

### 3.3 `src/types/memory.ts`

```ts
export interface MemoryItem {
  id: number;
  type: string;        // fact | preference | knowledge | event
  content: string;
  score?: number;      // 搜索时返回
  importance?: number;
  accessCount?: number;
  createTime?: string; // ISO datetime string
}

export interface MemorySearchResponse {
  query: string;
  items: MemoryItem[];
  total: number;
}

export interface MemoryListResponse {
  items: MemoryItem[];
  page: number;
  pageSize: number;
  total: number;
}
```

### 3.4 `src/api/memory.ts`

```ts
import { API_CONFIG } from '@/config/api-config';
import type { MemorySearchResponse, MemoryListResponse, MemoryItem } from '@/types/memory';

const BASE = API_CONFIG.BASE_URL;

async function handleResponse<T>(res: globalThis.Response): Promise<T> {
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const json = await res.json();
  if (json.code !== '0000') throw new Error(json.info || 'API error');
  return json.data;
}

export const memoryApi = {
  search: async (q: string, n = 10): Promise<MemorySearchResponse> => {
    const params = new URLSearchParams({ q, n: String(n) });
    const res = await fetch(`${BASE}/memory/search?${params}`, { credentials: 'include' });
    return handleResponse<MemorySearchResponse>(res);
  },

  list: async (page = 1, pageSize = 10): Promise<MemoryListResponse> => {
    const params = new URLSearchParams({ page: String(page), pageSize: String(pageSize) });
    const res = await fetch(`${BASE}/memory/list?${params}`, { credentials: 'include' });
    return handleResponse<MemoryListResponse>(res);
  },

  detail: async (id: number): Promise<MemoryItem> => {
    const res = await fetch(`${BASE}/memory/${id}`, { credentials: 'include' });
    return handleResponse<MemoryItem>(res);
  },

  delete: async (id: number): Promise<void> => {
    const res = await fetch(`${BASE}/memory/${id}`, { method: 'DELETE', credentials: 'include' });
    await handleResponse<string>(res);
  },
};
```

### 3.5 记忆管理页面 UI 设计

```
┌──────────────────────────────────────────────────────┐
│  [首页] [草稿] [文章] [流程图] [演示] [数据] [记忆] [我的]  │
├──────────────────────────────────────────────────────┤
│                                                      │
│  MEMORY                                              │
│  记忆管理                                            │
│                                                      │
│  ┌──────────────────────────────────────────────┐    │
│  │ 搜索记忆...                          [清除]  │    │
│  └──────────────────────────────────────────────┘    │
│                                                      │
│  共 11 条记忆                              [刷新]    │
│                                                      │
│  ┌──────────────────────────────────────────────┐    │
│  │ [偏好]  偏好使用 Markdown 撰写文档            │    │
│  │  ⚡ 0.61  ·  👁 2 次  ·  2 小时前    [删除]  │    │
│  ├──────────────────────────────────────────────┤    │
│  │ [事实]  使用 Redis 作为缓存数据库             │    │
│  │  ⚡ 0.59  ·  👁 1 次  ·  今天        [删除]  │    │
│  ├──────────────────────────────────────────────┤    │
│  │ [事件]  撰写 JVM 内存区域技术文章             │    │
│  │  ⚡ 0.54  ·  👁 1 次  ·  今天        [删除]  │    │
│  └──────────────────────────────────────────────┘    │
│                                                      │
│  ← 上一页  第 1/2 页  下一页 →                      │
│                                                      │
│  搜索时展示搜索结果（替换列表），清空搜索回列表      │
│                                                      │
└──────────────────────────────────────────────────────┘
```

### 3.6 页面功能清单

| 功能 | 说明 |
|------|------|
| 记忆列表 | `useEffect` 加载 `/memory/list`，展示类型标签+内容+元数据 |
| 搜索 | 500ms debounce，调 `/memory/search`，结果替换列表展示 |
| 搜索清除 | 清空输入后恢复分页列表模式 |
| 删除 | `window.confirm()` 确认后调 `DELETE`，成功刷新列表 |
| 分页 | 复用 `Pagination` 组件，props: `pageNo/pageSize/total/onChange` |
| 刷新 | 「刷新」按钮重新 fetch 当前页 |
| 类型标签 | 彩色标签：fact=蓝、preference=绿、knowledge=紫、event=黄 |
| 搜索分数 | 搜索模式下额外展示 `score` 字段 |
| loading | 骨架屏或 spinner |
| 空状态 | "暂无记忆，和 AI 对话后会自动提取记忆" |
| 错误处理 | API 失败 toast 提示 |
| 响应式 | 移动端卡片全宽，桌面端 max-w-3xl 居中 |

### 3.7 搜索防抖实现

```ts
const debounceTimer = useRef<NodeJS.Timeout | null>(null);
const [searchMode, setSearchMode] = useState(false);
const [searchQuery, setSearchQuery] = useState('');

const handleSearchChange = (value: string) => {
  setSearchQuery(value);
  if (debounceTimer.current) clearTimeout(debounceTimer.current);
  if (!value.trim()) {
    setSearchMode(false);
    loadList(1);
    return;
  }
  debounceTimer.current = setTimeout(async () => {
    setSearchMode(true);
    setLoading(true);
    try {
      const data = await memoryApi.search(value, 10);
      setItems(data.items);
      setTotal(data.total);
    } catch (e) { console.error(e); }
    finally { setLoading(false); }
  }, 500);
};
```

### 3.8 WorkspaceHeader 导航修改

`src/components/WorkspaceHeader/index.tsx` 变更：

1. `IconName` type 新增 `"memory"`
2. `NAV_ITEMS` 在"数据"和"我的"之间插入：`{ label: "记忆", path: "/memory", icon: "memory" }`
3. `NavIcon` 新增 memory 分支：

```tsx
if (name === "memory") {
  return <><path d="M12 2a7 7 0 0 1 7 7c0 2.8-1.6 5.2-4 6.3V17H9v-1.7C6.6 14.2 5 11.8 5 9a7 7 0 0 1 7-7Z"/><rect x="9" y="18" width="6" height="3" rx="1"/></>;
}
```

（灯泡图标 — 代表记忆/灵感）

### 3.9 类型标签配色

```ts
const TYPE_CONFIG: Record<string, { label: string; color: string; bg: string }> = {
  fact:       { label: '事实', color: 'text-blue-600', bg: 'bg-blue-50' },
  preference: { label: '偏好', color: 'text-green-600', bg: 'bg-green-50' },
  knowledge:  { label: '知识', color: 'text-purple-600', bg: 'bg-purple-50' },
  event:      { label: '事件', color: 'text-amber-600', bg: 'bg-amber-50' },
};
```

### 3.10 相对时间工具函数

新建 `src/utils/time.ts`：

```ts
export function relativeTime(dateStr: string): string {
  const now = Date.now();
  const target = new Date(dateStr).getTime();
  const diff = now - target;
  const minutes = Math.floor(diff / 60000);
  if (minutes < 1) return '刚刚';
  if (minutes < 60) return `${minutes} 分钟前`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} 小时前`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days} 天前`;
  if (days < 30) return `${Math.floor(days / 7)} 周前`;
  return new Date(dateStr).toLocaleDateString('zh-CN');
}
```

---

## 四、CORS 与 Cookie 跨域问题

### 4.1 当前状态

后端 `SecurityConfig.corsConfigurationSource()` 只允许 `http://localhost:*`。前端开发在 `http://localhost:3000` 发请求到 `http://localhost:8091`。

### 4.2 潜在问题

1. `credentials: 'include'` 要求 `Access-Control-Allow-Credentials: true` + `Access-Control-Allow-Origin` 不能为 `*`（当前用 `allowedOriginPatterns` 已正确）
2. Cookie `SameSite=Lax` + 同 localhost 不同端口 → 浏览器认为同站，cookie 可发送 ✅
3. httpOnly 的 `token` cookie 前端不可读，但 `ai_agent_login` 是 JS 可读的（用于前端路由守卫）

### 4.3 注意事项

- 确保后端 `token` cookie 设了 `path=/`（已确认）
- 前端 `proxy.ts` 检查的是 `ai_agent_login`（JS 可读），不是 httpOnly `token`
- 两个 cookie 需同时存在：`ai_agent_login`（前端路由守卫用）+ `token`（后端 JWT 验证用）

---

## 五、错误处理策略

| 场景 | 处理方式 |
|------|---------|
| API 返回 `code !== '0000'` | throw Error，页面级 catch 展示 toast |
| HTTP 401（JWT 过期） | 清除 cookie 跳转 `/login` |
| 网络异常 | 展示"网络错误，请稍后重试" |
| 流式中断 | 保留已接收内容，展示"生成中断" |
| 删除失败 | toast 提示但不刷新列表 |
| 搜索失败 | 不改变列表，toast 提示 |

### 401 拦截建议

`src/api/memory.ts` 的 `handleResponse` 中加入：

```ts
if (res.status === 401) {
  clearUserInfo();
  window.location.href = '/login';
  throw new Error('登录已过期');
}
```

---

## 六、可访问性(Accessibility)

| 元素 | a11y 处理 |
|------|---------|
| 搜索输入框 | `aria-label="搜索记忆"` + `role="searchbox"` |
| 删除按钮 | `aria-label="删除记忆: {content前20字}"` |
| 类型标签 | `role="status"` |
| 对话输入框 | `aria-label="输入消息"` |
| Tab 切换 | `role="tablist"` + `role="tab"` + `aria-selected` |

---

## 七、文件变更总览

| # | 文件 | 操作 | 预估时间 |
|---|------|:---:|:--:|
| 1 | `src/types/writing-chat.ts` | 新增 | 10min |
| 2 | `src/types/memory.ts` | 新增 | 10min |
| 3 | `src/utils/time.ts` | 新增 | 10min |
| 4 | `src/api/writing-chat.ts` | 新增 | 20min |
| 5 | `src/api/memory.ts` | 新增 | 15min |
| 6 | `src/components/WritingChatPanel/index.tsx` | 新增 | 2h |
| 7 | `src/components/AiWritingPanel/index.tsx` | 修改 | 1h |
| 8 | `src/app/memory/page.tsx` | 新增 | 1.5h |
| 9 | `src/app/drafts/[draftId]/page.tsx` | 修改 | 15min |
| 10 | `src/components/WorkspaceHeader/index.tsx` | 修改 | 15min |
| | **合计** | | **~6.5h** |

---

## 八、验证清单

### 8.1 多轮对话面板

- [ ] Tab 切换快捷操作/对话写作正常
- [ ] 创建会话成功（检查 console 无报错）
- [ ] 发消息后流式展示 Markdown
- [ ] 停止生成按钮生效
- [ ] 追加正文/替换正文按钮作用到编辑器
- [ ] 保存记忆按钮调用 save API + 显示"已记忆✓"
- [ ] 清空对话后重新发消息携带草稿上下文
- [ ] 快速连续发消息不会乱序
- [ ] 网络异常时展示错误信息

### 8.2 记忆管理页

- [ ] `/memory` 路由加载正常
- [ ] 列表展示记忆（类型+内容+重要性+访问+时间）
- [ ] 搜索 500ms 防抖生效（不会每个字符都请求）
- [ ] 搜索结果替换列表，清空搜索恢复列表
- [ ] 搜索结果展示匹配分数
- [ ] 删除确认弹窗 + 删除后列表刷新
- [ ] 分页切换正常
- [ ] 空状态文案展示
- [ ] 导航栏"记忆"高亮
- [ ] 移动端响应式布局正常

---

## 九、关键设计决策

### 9.1 Session 过期自动重连

后端 ADK Session 存在内存中，重启后所有 sessionId 失效。前端需处理：

```ts
// WritingChatPanel 中：
const handleStreamError = async (err: Error) => {
  if (err.message.includes('Session not found') || err.message.includes('500')) {
    // 自动重建 session
    try {
      const newSid = await writingChatApi.createSession(draftId);
      setSessionId(newSid);
      // 提示用户重新发送（不自动重发，避免重复消费 token）
      setMessages(prev => [...prev, { role: 'assistant', content: '[会话已过期，已自动重连，请重新发送消息]' }]);
    } catch { setSessionError('重连失败'); }
  } else {
    setMessages(prev => [...prev, { role: 'assistant', content: `[错误] ${err.message}` }]);
  }
  setStreaming(false);
};
```

### 9.2 Tab 切换保持状态（CSS 隐藏代替条件渲染）

条件渲染 `{mode === 'chat' ? <WritingChatPanel/> : null}` 会销毁组件丢失对话。改用 CSS hidden：

```tsx
<div className={mode === 'quick' ? '' : 'hidden'}>
  {/* 现有快捷操作 UI */}
</div>
<div className={mode === 'chat' ? '' : 'hidden'}>
  <WritingChatPanel ... />
</div>
```

两个面板始终挂载，Tab 切换仅改变 `hidden` class。对话 session 和消息列表在切换间保持。

### 9.3 搜索分数 vs 列表重要性的展示区分

| 模式 | 展示字段 | UI 呈现 |
|------|---------|---------|
| 列表模式 | `importance` + `accessCount` + `createTime` | "重要性 0.61 · 访问 2 次 · 2h前" |
| 搜索模式 | `score` + `importance` | "匹配 0.69 · 重要性 0.61" |

搜索结果没有分页（后端一次返回 topK），列表模式有分页。

### 9.4 并发流竞态防护

```ts
const requestId = useRef(0);

const handleSend = useCallback(async () => {
  // 取消上一个未完成的流
  controllerRef.current?.abort();
  
  const currentId = ++requestId.current;
  // ... 发起新的 streamChat ...
  
  // 所有回调中检查：
  onToken: (token) => {
    if (requestId.current !== currentId) return;  // 过期请求，忽略
    setStreamBuffer(prev => prev + token);
  },
}, [/* deps */]);
```

### 9.5 记忆注入效果展示（后续迭代）

当前 V2 暂不实现。后续可在 `/writing/chat/stream` 的第一个 chunk 增加元信息：

```json
{"chunk": {"type": "meta", "memoriesInjected": 3}}
```

前端收到后在 AI 回复区域顶部展示："已注入 3 条相关记忆"。需后端配合修改 `WritingChatController.chatStream()`。

---

## 十、注意事项

1. **JWT 鉴权**：所有请求 `credentials: 'include'`，不传 `userId` 参数
2. **搜索 URL 编码**：中文用 `URLSearchParams` 自动编码
3. **面板状态保持**：用 `hidden` CSS 而非条件渲染，Tab 切换不销毁组件
4. **流式 buffer**：TCP 粘包用 `buffer.split('\n')` + `lines.pop()` 防截断
5. **删除确认**：`window.confirm()`，不自定义 Modal
6. **对话历史本地**：刷新丢失（V2 暂不实现前端恢复）
7. **Pagination 组件**：复用现有，props 是 `pageNo/pageSize/total/onChange`
8. **MarkdownRenderer**：AI 回复用 `stream={true}` 模式实时渲染
9. **错误边界**：401 清 cookie 跳 login，Session not found 自动重连
10. **Dark mode**：使用 CSS 变量，无需额外处理
11. **并发防护**：发送前 abort 上一个流 + requestId 校验
12. **搜索/列表模式**：搜索展示 score，列表展示 importance，字段不同
