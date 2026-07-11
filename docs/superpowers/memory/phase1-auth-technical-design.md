# 阶段一：真实用户认证体系 — 技术设计文档

> 2026-07-11 | 基于 `final_plan.md` 第 4 节展开

## 0. 基线状态

### 已具备

| 资产 | 位置 | 状态 |
|------|------|------|
| `user` 表 DDL | `docs/dev-ops/mysql/sql/01-sutone-agent-bok-phase1.sql:16-27` | 已建表，含 `id/username/password_hash/nickname/avatar_url`，`uk_username` 唯一索引 |
| JWT 依赖 | `pom.xml` → `jjwt 0.9.1` + `java-jwt 4.4.0` | 已声明，**从未使用** |
| 领域层 owner 校验 | `DraftEntity.validateOwner()`、`AiTaskEntity.validateOwner()` | 已实现，接受 `Long userId` 参数 |
| 领域服务签名 | `IDraftDomainService`、`IAiWritingService`、`IPublishDomainService` | 所有方法已接受 `Long userId` 参数 |

### 待替换

| 位置 | 当前值 | 需改为 |
|------|--------|--------|
| `AiWritingController.java:37` | `DEFAULT_USER_ID = 1L` | JWT 解析 |
| `ContentDraftController.java:29` | `DEFAULT_USER_ID = 1L` | JWT 解析 |
| `ArticleController.java:31` | `DEFAULT_USER_ID = 1L` | JWT 解析 |
| `user` 表种子数据 | `password_hash = 'admin'`（明文） | BCrypt 哈希 |

### 待新建

- `spring-boot-starter-security`（pom）
- `UserEntity`、`UserPO`、`IUserDao`、`IUserRepository`、`UserDomainService`
- `JwtTokenProvider`、`JwtAuthenticationFilter`
- `SecurityConfig`
- `AuthController`（login/register）+ DTO
- 前端登录页 + token 管理

---

## 1. 架构总览

```
┌─────────────────────────────────────────────────────────────────┐
│ 前端 (Next.js)                                                   │
│  LoginPage: POST /api/v1/auth/login → 后端 Set-Cookie(token)    │
│  所有 API 请求: 浏览器自动带 Cookie（前端零代码）                    │
│  middleware.ts: 读 cookie 做路由拦截                               │
├─────────────────────────────────────────────────────────────────┤
│ 后端 (Spring Boot)                                               │
│                                                                  │
│  SecurityConfig                                                  │
│    ├── JwtAuthenticationFilter (OncePerRequestFilter)             │
│    │     └─ 从 Cookie header 解析 token → 注入 SecurityContext    │
│    ├── 白名单: /api/v1/auth/login, /api/v1/auth/register         │
│    └── 其他全部接口需认证                                         │
│                                                                  │
│  AuthController                                                  │
│    POST /auth/login   → LoginRequestDTO                          │
│                        → Set-Cookie(token; HttpOnly; Secure;     │
│                          SameSite=Strict; Max-Age=7200)          │
│                        → 返回 { userId, username, nickname }     │
│    POST /auth/register → RegisterRequestDTO → user              │
│                                                                  │
│  Controller 层                                                   │
│    替换: DEFAULT_USER_ID → getCurrentUserId()                    │
│    从 SecurityContextHolder 获取，不再是硬编码常量                  │
│                                                                  │
│  Domain 层                                                       │
│    不改！已有 validateOwner(userId) — 天然支持越权防护              │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. 新建文件清单（按 DDD 分层）

### 2.1 API 层 (sutone-agent-bok-api)

```
api/dto/auth/
├── LoginRequestDTO.java          # { username, password }
├── LoginResponseDTO.java         # { userId, username, nickname }  (token 走 httpOnly Cookie，不入 body)
├── RegisterRequestDTO.java       # { username, password, nickname }
└── RegisterResponseDTO.java      # { userId, username }
```

### 2.2 Domain 层 (sutone-agent-bok-domain)

```
domain/account/
├── model/entity/UserEntity.java          # 用户领域实体
├── model/valobj/UserStatusVO.java        # 用户状态值对象 (ACTIVE/DISABLED)
├── adapter/repository/IUserRepository.java  # 仓储接口
└── service/UserDomainService.java           # 登录/注册/查询
```

### 2.3 Infrastructure 层 (sutone-agent-bok-infrastructure)

```
infrastructure/
├── dao/IUserDao.java              # MyBatis 映射 (select/password/insert)
├── dao/po/UserPO.java             # 持久化对象
└── adapter/repository/UserRepository.java  # 仓储实现
```

### 2.4 Trigger 层 (sutone-agent-bok-trigger)

```
trigger/http/
└── AuthController.java            # POST /auth/login, /auth/register

trigger/security/                   # 新建包
├── JwtTokenProvider.java          # token 签发、解析、校验
├── JwtAuthenticationFilter.java   # 过滤器：从 header 提取 token → 注入上下文
├── SecurityConfig.java            # Spring Security 配置
├── CurrentUserResolver.java       # @CurrentUser 参数解析器（可选）
├── CurrentUser.java               # 自定义注解（可选）
└── AuthUtil.java                  # 静态工具: getCurrentUserId()
```

### 2.5 App 层 (sutone-agent-bok-app)

```
pom.xml                             # 新增 spring-boot-starter-security
resources/application.yml           # jwt.secret, jwt.expiration 配置
```

---

## 3. 核心类设计

### 3.1 UserPO（infrastructure/dao/po）

```java
@Data
public class UserPO {
    private Long id;
    private String username;
    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
```

### 3.2 IUserDao（infrastructure/dao，注解式 MyBatis）

```java
@Mapper
public interface IUserDao {

    @Select("SELECT id, username, password_hash, nickname, avatar_url, create_time, update_time, is_deleted " +
            "FROM user WHERE id = #{id} AND is_deleted = 0")
    UserPO selectById(@Param("id") Long id);

    @Select("SELECT id, username, password_hash, nickname, avatar_url, create_time, update_time, is_deleted " +
            "FROM user WHERE username = #{username} AND is_deleted = 0")
    UserPO selectByUsername(@Param("username") String username);

    @Insert("INSERT INTO user(username, password_hash, nickname, avatar_url, is_deleted) " +
            "VALUES(#{username}, #{passwordHash}, #{nickname}, #{avatarUrl}, 0)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserPO userPO);

    @Select("SELECT IFNULL(MAX(id), 0) + 1 FROM user")
    Long nextId();
}
```

### 3.3 UserEntity（domain/account/model/entity）

```java
@Data
@Builder
public class UserEntity {
    private Long userId;
    private String username;
    private String passwordHash;   // BCrypt 哈希，仅内部使用
    private String nickname;
    private String avatarUrl;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // 创建新用户
    public static UserEntity register(String username, String rawPassword, String nickname) {
        return UserEntity.builder()
                .username(username)
                .passwordHash(BCrypt.hashpw(rawPassword, BCrypt.gensalt()))
                .nickname(null == nickname ? username : nickname)
                .build();
    }

    // 校验密码
    public boolean checkPassword(String rawPassword) {
        return BCrypt.checkpw(rawPassword, this.passwordHash);
    }
}
```

### 3.4 JwtTokenProvider（trigger/security）

```java
@Component
public class JwtTokenProvider {

    // 从 application.yml 注入
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")  // 单位: 毫秒，建议 7200000 (2小时)
    private long expiration;

    // 签发 token
    public String generateToken(Long userId, String username) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("username", username)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expiration))
                .signWith(SignatureAlgorithm.HS256, secret.getBytes(StandardCharsets.UTF_8))
                .compact();
    }

    // 从 token 解析 userId
    public Long getUserIdFromToken(String token) {
        return Long.valueOf(
            Jwts.parser()
                .setSigningKey(secret.getBytes(StandardCharsets.UTF_8))
                .parseClaimsJws(token)
                .getBody()
                .getSubject()
        );
    }

    // 校验 token 有效性
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .setSigningKey(secret.getBytes(StandardCharsets.UTF_8))
                .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
```

### 3.5 JwtAuthenticationFilter（trigger/security）— 从 httpOnly Cookie 读 token

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String TOKEN_COOKIE = "token";

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        String token = extractTokenFromCookie(request);

        if (token != null && jwtTokenProvider.validateToken(token)) {
            Long userId = jwtTokenProvider.getUserIdFromToken(token);
            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        chain.doFilter(request, response);
    }

    private String extractTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (TOKEN_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
```

### 3.6 SecurityConfig（trigger/security）

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm ->
                sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()   // 登录/注册
                .requestMatchers("/api/v1/query_ai_agent_config_list").permitAll()  // 可选
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

### 3.7 AuthUtil（trigger/security）— Controller 层替换 DEFAULT_USER_ID 的关键

```java
public final class AuthUtil {

    private AuthUtil() {}

    public static Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal();
        if (principal instanceof Long userId) {
            return userId;
        }
        throw new AppException(ResponseCode.UNAUTHORIZED.getCode(), "未登录或 token 已过期");
    }
}
```

### 3.8 AuthController（trigger/http）— login 通过 httpOnly Cookie 下发 token

```java
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private UserDomainService userDomainService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @PostMapping("/login")
    public Response<LoginResponseDTO> login(@RequestBody LoginRequestDTO request,
                                            HttpServletResponse response) {
        UserEntity user = userDomainService.login(
            request.getUsername(), request.getPassword());

        String token = jwtTokenProvider.generateToken(user.getUserId(), user.getUsername());

        // token 不下发到 body，只写入 httpOnly Cookie
        Cookie cookie = new Cookie("token", token);
        cookie.setHttpOnly(true);        // JS 不可读 → XSS 偷不走
        cookie.setSecure(false);         // 本地开发用 HTTP，生产部署改为 true（HTTPS 才传）
        cookie.setPath("/");             // 全站路径
        cookie.setMaxAge(7200);          // 2小时，与 JWT expiration 一致
        cookie.setAttribute("SameSite", "Strict");  // 防 CSRF
        response.addCookie(cookie);

        return Response.<LoginResponseDTO>builder()
            .code(ResponseCode.SUCCESS.getCode())
            .data(LoginResponseDTO.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .build())
            .build();
    }

    @PostMapping("/logout")
    public Response<Void> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("token", "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);             // 立即过期 → 浏览器删除
        response.addCookie(cookie);
        return Response.<Void>builder().code(ResponseCode.SUCCESS.getCode()).build();
    }

    @PostMapping("/register")
    public Response<RegisterResponseDTO> register(@RequestBody RegisterRequestDTO request) {
        UserEntity user = userDomainService.register(
            request.getUsername(), request.getPassword(), request.getNickname());

        return Response.<RegisterResponseDTO>builder()
            .code(ResponseCode.SUCCESS.getCode())
            .data(RegisterResponseDTO.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .build())
            .build();
    }
}
```

### 3.9 UserDomainService（domain/account/service）

```java
@Service
public class UserDomainService {

    private final IUserRepository userRepository;

    public UserDomainService(IUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserEntity login(String username, String rawPassword) {
        UserEntity user = userRepository.findByUsername(username);
        if (null == user) {
            throw new AppException(ResponseCode.UNAUTHORIZED.getCode(), "用户名或密码错误");
        }
        if (!user.checkPassword(rawPassword)) {
            throw new AppException(ResponseCode.UNAUTHORIZED.getCode(), "用户名或密码错误");
        }
        return user;
    }

    public UserEntity register(String username, String rawPassword, String nickname) {
        UserEntity existing = userRepository.findByUsername(username);
        if (null != existing) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "用户名已存在");
        }
        UserEntity user = UserEntity.register(username, rawPassword, nickname);
        userRepository.save(user);
        return user;
    }

    public UserEntity queryById(Long userId) {
        return userRepository.findById(userId);
    }
}
```

---

## 4. Controller 改造（替换 DEFAULT_USER_ID）

改造模板（以 `ContentDraftController` 为例）：

```java
// 改前
private static final Long DEFAULT_USER_ID = 1L;

public Response<SaveDraftResponseDTO> saveDraft(@RequestBody SaveDraftRequestDTO requestDTO) {
    // ...
    DraftEntity draft = draftDomainService.saveDraft(DEFAULT_USER_ID, command);

// 改后
// 删除 DEFAULT_USER_ID 常量

public Response<SaveDraftResponseDTO> saveDraft(@RequestBody SaveDraftRequestDTO requestDTO) {
    // ...
    Long userId = AuthUtil.getCurrentUserId();   // ← 从 SecurityContext 取
    DraftEntity draft = draftDomainService.saveDraft(userId, command);
```

**影响范围**：3 个 Controller，共 11 处调用点（见基线状态表格）。**Domain 层零改动**。

---

## 5. 配置文件

### 5.1 application.yml 新增

```yaml
jwt:
  secret: ${JWT_SECRET:your-default-secret-key-at-least-256-bits}
  expiration: 7200000  # 2小时 (毫秒)
```

生产环境通过环境变量 `JWT_SECRET` 覆盖，不硬编码进仓库。

### 5.2 pom.xml（app 模块）新增

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

已有 `jjwt:0.9.1` 无需额外加。

---

## 6. 前端改造

httpOnly Cookie 方案下**前端极简**——不需要 `token.ts`、不需要 `request.ts` wrapper、不需要手动拼 `Authorization` header。浏览器自动带 cookie。

### 6.1 新增文件

```
src/api/auth.ts              # authApi.login() / authApi.logout() / authApi.register()
src/app/login/page.tsx       # 登录页面
```

### 6.2 Token 管理流程

```
登录成功 → 后端 Set-Cookie: token=xxx; HttpOnly; SameSite=Strict
每次请求 → 浏览器自动带 Cookie（前端零代码）
登出     → 后端 Set-Cookie: token=; Max-Age=0 → 浏览器删除
用户信息 → login 接口返回 { userId, username, nickname }，存入 React state / context
```

### 6.3 前端页面路由保护（middleware.ts）

```typescript
// src/middleware.ts —— 服务端读 cookie，无需 localStorage
export function middleware(request: NextRequest) {
  const token = request.cookies.get("token")?.value;
  const isAuthPage = request.nextUrl.pathname.startsWith("/login");

  if (!token && !isAuthPage) {
    return NextResponse.redirect(new URL("/login", request.url));
  }
  if (token && isAuthPage) {
    return NextResponse.redirect(new URL("/drafts", request.url));
  }
}

export const config = {
  matcher: ["/((?!api|_next|static|favicon).*)"],
};
```

---

## 7. 实施步骤（建议顺序，预计 4-5 天）

| 步骤 | 内容 | 预估 |
|------|------|------|
| 1 | 新建 `UserPO` + `IUserDao` + `UserEntity` + `IUserRepository` + `UserRepository` | 0.5 天 |
| 2 | 新建 `UserDomainService`（含 BCrypt 登录/注册逻辑） | 0.5 天 |
| 3 | 新建 `JwtTokenProvider` + `JwtAuthenticationFilter`(Cookie) + `SecurityConfig` + `AuthUtil` | 0.5 天 |
| 4 | 新建 `AuthController`（login: Set-Cookie + logout: clear cookie）+ API DTO | 0.5 天 |
| 5 | pom 加 security 依赖 + yml 加 jwt 配置 | 0.2 天 |
| 6 | 改造 3 个 Controller（11 处 `DEFAULT_USER_ID` → `AuthUtil.getCurrentUserId()`） | 0.3 天 |
| 7 | 数据库 ALTER：`password_hash` 改为 BCrypt（用脚本把 `admin` 的种子数据哈希化） | 0.2 天 |
| 8 | 前端：auth api + 登录页 + middleware 路由保护（无需 token.ts/request.ts） | 0.5 天 |
| 9 | 联调 + 回归（草稿 CRUD / AI 写作 / 文章发布） | 1 天 |

---

## 8. 面试可讲的点

1. **无状态鉴权**：JWT + Spring Security 过滤器链，不依赖服务端 session
2. **越权防护**：`validateOwner(userId)` — 从 token 解析的真实 userId 校验，拒绝水平越权（改 ID 访问他人资源）
3. **密码安全**：BCrypt 加盐哈希，绝不明文存储
4. **DDD 一致性**：Auth 模块同样走 domain/adapter/infrastructure 分层，与现有草稿/文章模块一致
5. **零 Domain 层改动**：Controller 层改 `DEFAULT_USER_ID` → `AuthUtil.getCurrentUserId()`，Domain/Infrastructure 的 `userId` 参数天然兼容

---

## 9. 风险与注意事项

| 风险 | 缓解 |
|------|------|
| `jjwt 0.9.1` 版本较老 | 功能足够（签发/解析/校验），后续可升级到 0.12.x |
| 种子数据 `admin/admin` 是明文 | 步骤 7 用 BCrypt 重哈希 |
| AJAX fetch 跨域不带 cookie | `credentials: "same-origin"`（同源默认带），或 `"include"`（跨域） |
| SSE（EventSource）不传自定义 header | Cookie 方案天然解决——SSE 请求自动带 cookie，无需自定义 Authorization |
| 本地开发跨端口（前端 :3000 → 后端 :8091） | 前端 `next.config.ts` 配 `rewrites` 代理 `/api` 到后端，避免跨域 cookie 问题 |
| WebSocket（未来协同）需要 JWT | 握手时 cookie 自动发送，filter 同样校验 |
