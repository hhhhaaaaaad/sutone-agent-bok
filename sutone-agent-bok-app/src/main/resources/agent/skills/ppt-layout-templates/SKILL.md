---
name: ppt-layout-templates
description: PPT页面布局模板库v3。当AI需要生成PPT页面时，必须参考此技能选择布局并使用丰富的元素类型。支持7种元素类型（text/table/icon/divider/bullet/shape/image），12种布局模板，以及3种场景化专业模板（技术分享、教学课件、个人简历）。重点：用icon和bullet让页面生动，用divider增加层次，不再只输出纯文字。
license: Apache-2.0
metadata:
  author: sdh-studio
  version: "3.0.0"
  category: ppt-design
---

# PPT 页面布局模板库 v3

## 核心升级（vs v2）

**v2 的问题**：AI 只输出 text + table，页面全是纯文字+色块，像 Word 文档不像 PPT。
**v3 的改变**：AI 可以输出 7 种元素类型，用 icon/divider/bullet 丰富视觉层次。

## 画布规格

- 尺寸：13.33 × 7.5 英寸（16:9 宽屏）
- 所有坐标、尺寸单位为英寸
- 前端自动渲染主题装饰（色块、条纹、底栏、卡片、圆点等）
- **AI 可以输出 shape 用于自主装饰**（但优先用 icon/divider/bullet）

---

## 元素类型速查（7种）

| kind | 用途 | 必填字段 | 示例 |
|------|------|---------|------|
| `text` | 文字内容 | content, x, y, w, h, fontSize, color | 标题、正文、描述 |
| `table` | 表格数据 | content, x, y, w, h, rows | 对比表、数据表 |
| `icon` | 图标/emoji | content, x, y, w, h, fontSize, color | 卡片顶部图标、章节图标 |
| `divider` | 分割线 | x, y, w, h, color, thickness | 区域分隔、装饰线 |
| `bullet` | 编号条目 | content, x, y, w, h, number, fill | 带编号圆圈的要点 |
| `shape` | 自主装饰矩形 | x, y, w, h, fill | 背景块、高亮条（支持 radius/shadow/opacity） |
| `image` | 图片占位 | content(URL), x, y, w, h | 图表、截图 |

### icon 可用 emoji 列表（按场景）

**技术分享**：🚀 💡 🔧 ⚡ 🏗️ 🔗 📊 🎯 🔐 📱 💻 🌐 📦 🔄 ⚙️ 🛠️ 📈 🔍 🗂️ 💾
**教学课件**：📚 🎓 📝 ✏️ 🧠 📖 🔬 🎨 🌍 💡 🏆 📋 ✅ 📌 🔑 🎯 📊 🗺️ ⭐ 🌟
**商务汇报**：📈 💼 🏢 🎯 💰 🤝 📊 🏆 💡 📋 ⭐ 🔑 🚀 ✅ 📌 🔍 📦 💎 🌟 🎖️
**个人简历**：👤 💼 🎓 🏆 💡 🔧 📊 🎯 ✅ 📞 📧 🌐 📝 🏅 ⭐ 🚀 💎 🏢 🤝 📋

### divider 用法

```json
// 水平分割线（w > h）
{"kind":"divider","content":"","x":1.0,"y":3.0,"w":11.0,"h":0.04,"color":"AAAAAA","thickness":0.04}
// 竖直分割线（h > w）
{"kind":"divider","content":"","x":6.6,"y":1.0,"w":0.04,"h":5.5,"color":"AAAAAA","thickness":0.04}
```

### bullet 用法（带编号圆圈的要点，比纯文字列表更生动）

```json
{"kind":"bullet","content":"微服务架构设计","x":5.0,"y":1.5,"w":7.5,"h":0.8,"number":1,"fill":"1F3864","fontSize":16,"color":"333333"}
{"kind":"bullet","content":"容器化部署方案","x":5.0,"y":2.5,"w":7.5,"h":0.8,"number":2,"fill":"1F3864","fontSize":16,"color":"333333"}
{"kind":"bullet","content":"性能优化实践","x":5.0,"y":3.5,"w":7.5,"h":0.8,"number":3,"fill":"1F3864","fontSize":16,"color":"333333"}
```

---

## 布局模板一览

| layout 值 | 用途 | 装饰骨架（前端自动渲染） |
|-----------|------|------------------------|
| `title_slide` | 封面/首页 | 渐变上半部主色块 + accent条纹 + 底栏 + 半透明装饰圆 |
| `content_slide` | 要点/列表 | 左侧渐变色带(w:4.5) + accent竖线 + 底栏 |
| `content_top` | 顶部标题内容页 | 渐变头栏(h:1.2) + accent条纹 + 底栏 |
| `card_3col` | 三列并列 | 渐变头栏 + 三列圆角阴影卡片 + 底栏 |
| `card_2col` | 两列并列 | 渐变头栏 + 两列圆角阴影卡片 + 底栏 |
| `comparison` | 左右对比 | 渐变头栏 + 左右卡片 + accent中间线 + 底栏 |
| `timeline` | 时间轴/流程 | 左侧渐变竖条 + 水平轴线+圆点 + 底栏 |
| `quote_slide` | 金句/引言 | 渐变左侧宽条 + offWhite背景 + 底栏 |
| `data_highlight` | 大数字/KPI | 渐变窄头栏 + 指标区 + 底部色带 + 底栏 |
| `end_slide` | 结尾/致谢 | 同 title_slide |

---

## 🌟 场景化专业模板

### 场景1：技术分享 PPT

**典型结构**（8-10页）：

1. **封面** `title_slide` — 主标题 + 副标题 + 演讲者
2. **目录** `content_top` — 用 bullet 列出 4 个章节，每个前面加 icon
3. **章节封面** `content_slide` — 左侧色带放章节标题 + icon，右侧放章节要点
4. **技术要点** `content_top` — 头栏放标题，下方用 bullet+icon 排列
5. **架构对比** `card_2col` — 左右对比新旧架构
6. **代码/流程** `content_top` — 用 divider 分区，上面放流程说明，下面放要点
7. **数据成果** `data_highlight` — 3 个大数字 KPI
8. **总结** `content_slide` — 左侧色带放"核心要点"，右侧用 bullet 编号
9. **致谢** `end_slide`

**技术分享示例页 — 章节要点**：
```json
[
  {"kind":"text","content":"🏗️ 微服务架构演进","x":0.5,"y":0.15,"w":12.3,"h":0.7,"fontSize":24,"color":"FFFFFF","bold":true,"align":"left"},
  {"kind":"divider","content":"","x":0.5,"y":1.1,"w":12.3,"h":0.04,"color":"FFFFFF","thickness":0.04,"opacity":0.3},
  {"kind":"icon","content":"🔧","x":0.8,"y":1.6,"w":0.8,"h":0.8,"fontSize":28,"color":"1F3864"},
  {"kind":"text","content":"服务拆分策略","x":1.8,"y":1.7,"w":5.0,"h":0.6,"fontSize":18,"color":"333333","bold":true,"align":"left"},
  {"kind":"text","content":"按业务域划分，避免分布式单体","x":1.8,"y":2.2,"w":5.0,"h":0.5,"fontSize":14,"color":"666666","align":"left"},
  {"kind":"icon","content":"⚡","x":7.2,"y":1.6,"w":0.8,"h":0.8,"fontSize":28,"color":"1F3864"},
  {"kind":"text","content":"异步通信机制","x":8.2,"y":1.7,"w":5.0,"h":0.6,"fontSize":18,"color":"333333","bold":true,"align":"left"},
  {"kind":"text","content":"事件驱动 + 消息队列解耦","x":8.2,"y":2.2,"w":5.0,"h":0.5,"fontSize":14,"color":"666666","align":"left"},
  {"kind":"icon","content":"🔐","x":0.8,"y":3.2,"w":0.8,"h":0.8,"fontSize":28,"color":"1F3864"},
  {"kind":"text","content":"服务网关安全","x":1.8,"y":3.3,"w":5.0,"h":0.6,"fontSize":18,"color":"333333","bold":true,"align":"left"},
  {"kind":"text","content":"统一鉴权、限流、熔断","x":1.8,"y":3.8,"w":5.0,"h":0.5,"fontSize":14,"color":"666666","align":"left"},
  {"kind":"icon","content":"📊","x":7.2,"y":3.2,"w":0.8,"h":0.8,"fontSize":28,"color":"1F3864"},
  {"kind":"text","content":"可观测性建设","x":8.2,"y":3.3,"w":5.0,"h":0.6,"fontSize":18,"color":"333333","bold":true,"align":"left"},
  {"kind":"text","content":"链路追踪 + 指标监控 + 日志聚合","x":8.2,"y":3.8,"w":5.0,"h":0.5,"fontSize":14,"color":"666666","align":"left"}
]
```

### 场景2：高校教学 PPT

**典型结构**（15-20页，每章4-5页）：

1. **课程封面** `title_slide` — 课程名 + 教师 + 学期
2. **本章概览** `content_top` — 标题 + 3-4 个 bullet 要点
3. **核心概念** `card_3col` — 三个概念卡片，每个顶部有 icon
4. **原理讲解** `content_slide` — 左侧标题+关键术语，右侧详细说明
5. **对比分析** `comparison` — 理论A vs 理论B
6. **案例展示** `content_top` — 案例描述 + divider + 分析要点
7. **知识总结** `content_slide` — 左侧"本章要点"，右侧 bullet 编号列表
8. **练习/思考** `quote_slide` — 思考题

**教学示例页 — 核心概念卡片**：
```json
[
  {"kind":"text","content":"📚 三大核心概念","x":0.5,"y":0.1,"w":12.3,"h":0.7,"fontSize":24,"color":"FFFFFF","bold":true,"align":"left"},
  {"kind":"icon","content":"🧠","x":1.8,"y":1.5,"w":1.5,"h":1.2,"fontSize":36,"color":"1F3864","align":"center"},
  {"kind":"text","content":"认知负荷理论","x":0.6,"y":2.8,"w":3.9,"h":0.6,"fontSize":16,"color":"333333","bold":true,"align":"center"},
  {"kind":"text","content":"工作记忆容量有限，教学设计需降低外在认知负荷","x":0.6,"y":3.5,"w":3.9,"h":2.5,"fontSize":13,"color":"666666","align":"center"},
  {"kind":"icon","content":"🔬","x":6.0,"y":1.5,"w":1.5,"h":1.2,"fontSize":36,"color":"1F3864","align":"center"},
  {"kind":"text","content":"建构主义学习","x":4.7,"y":2.8,"w":3.9,"h":0.6,"fontSize":16,"color":"333333","bold":true,"align":"center"},
  {"kind":"text","content":"学习者主动构建知识，而非被动接受信息","x":4.7,"y":3.5,"w":3.9,"h":2.5,"fontSize":13,"color":"666666","align":"center"},
  {"kind":"icon","content":"🎯","x":10.2,"y":1.5,"w":1.5,"h":1.2,"fontSize":36,"color":"1F3864","align":"center"},
  {"kind":"text","content":"最近发展区","x":8.9,"y":2.8,"w":3.9,"h":0.6,"fontSize":16,"color":"333333","bold":true,"align":"center"},
  {"kind":"text","content":"在教师引导下，学生可以达到的潜在发展水平","x":8.9,"y":3.5,"w":3.9,"h":2.5,"fontSize":13,"color":"666666","align":"center"}
]
```

### 场景3：个人简历 PPT

**典型结构**（5-6页）：

1. **封面** `title_slide` — 姓名 + 职位 + 联系方式
2. **个人简介** `content_slide` — 左侧照片区(icon占位) + 姓名/职位，右侧个人摘要
3. **工作经历** `timeline` — 3-4 段经历，时间轴排列
4. **核心技能** `card_3col` — 三列技能卡片，每列顶部有 icon
5. **项目成果** `data_highlight` — 量化数据（效率提升xx%，用户增长xx%）
6. **教育/致谢** `end_slide`

**简历示例页 — 核心技能**：
```json
[
  {"kind":"text","content":"核心技能","x":0.5,"y":0.1,"w":12.3,"h":0.7,"fontSize":24,"color":"FFFFFF","bold":true,"align":"left"},
  {"kind":"icon","content":"💻","x":1.8,"y":1.4,"w":1.5,"h":1.0,"fontSize":32,"color":"1F3864"},
  {"kind":"text","content":"技术栈","x":0.6,"y":2.5,"w":3.9,"h":0.5,"fontSize":16,"color":"333333","bold":true,"align":"center"},
  {"kind":"text","content":"Java · Spring Boot\nReact · TypeScript\nMySQL · Redis\nDocker · K8s","x":0.6,"y":3.1,"w":3.9,"h":3.0,"fontSize":13,"color":"666666","align":"center"},
  {"kind":"icon","content":"🏗️","x":6.0,"y":1.4,"w":1.5,"h":1.0,"fontSize":32,"color":"1F3864"},
  {"kind":"text","content":"架构设计","x":4.7,"y":2.5,"w":3.9,"h":0.5,"fontSize":16,"color":"333333","bold":true,"align":"center"},
  {"kind":"text","content":"微服务架构\n分布式系统设计\n高并发方案\nDDD 领域驱动","x":4.7,"y":3.1,"w":3.9,"h":3.0,"fontSize":13,"color":"666666","align":"center"},
  {"kind":"icon","content":"🚀","x":10.2,"y":1.4,"w":1.5,"h":1.0,"fontSize":32,"color":"1F3864"},
  {"kind":"text","content":"工程效能","x":8.9,"y":2.5,"w":3.9,"h":0.5,"fontSize":16,"color":"333333","bold":true,"align":"center"},
  {"kind":"text","content":"CI/CD 流水线\n性能优化\n代码质量治理\n技术团队管理","x":8.9,"y":3.1,"w":3.9,"h":3.0,"fontSize":13,"color":"666666","align":"center"}
]
```

---

## 关键约束

0. **严格遵守页数要求**：用户要求多少页，你必须输出**完整对应页数**的 JSON，**绝对不能少页**！如果内容过多，拆分到多页。
0.1. **合理排版避免堆叠**：请务必合理分配元素的坐标 (x, y) 和宽高 (w, h)，**同一页内的元素绝对不能重叠**。
1. **元素多样性**：每个内容页至少使用 1 个 icon 或 bullet 元素，不要全是纯文字
2. **布局多样性**：一份5页以上的PPT，至少使用3种不同的layout
3. **颜色规则**（按装饰骨架区分）：
   - 全幅色块区（title/quote/end封面部分） → color=FFFFFF
   - content_slide 左侧色带(x<4.5) → color=FFFFFF；右侧区域(x≥5.0) → color=333333
   - content_top 头栏 → color=FFFFFF
   - card_3col/2col 头栏 → color=FFFFFF；卡片标题 → color=FFFFFF 或 333333；描述 → color=666666
   - comparison 头栏 → color=FFFFFF；卡片内容 → color=333333
   - timeline 标题 → color=FFFFFF；节点内容 → color=333333/666666
   - data_highlight 头栏 → color=FFFFFF；大数字 → color=FFFFFF；说明 → color=666666
   - icon 颜色 → 用 primary 色（如 1F3864）
   - divider 颜色 → 用 lightGray(AAAAAA) 或 primaryLight
   - bullet 圆圈 fill → 用 primary 色，文字 color → 333333
4. **坐标安全**：x+w ≤ 13.33，y+h ≤ 7.5，元素不重叠
5. **字号规范**：封面标题40-48，内容页标题24-30，正文13-18，大数字36-52，icon 28-36
6. **icon 优先**：能用 icon 的地方不要只用文字——卡片顶部、要点前面、章节封面都应该有 icon
7. **bullet 优先**：有序步骤、要点列表，用 bullet 比 text+• 更生动
8. **divider 分层**：内容较多的页面，用 divider 分区增加层次感
