---
name: ppt-color-theory
description: PPT配色理论与实践指南。当AI生成PPT时，必须参考此技能确保配色专业、协调、有层次感。包含色彩心理学、5套主题配色方案详解、以及配色应用规则。
license: Apache-2.0
metadata:
  author: sdh-studio
  version: "1.0.0"
  category: ppt-design
---

# PPT 配色理论与实践

## 核心原则

1. **60-30-10 法则**：主色60% + 辅助色30% + 强调色10%
2. **对比度优先**：文字与背景必须保持足够对比度（WCAG AA标准）
3. **情绪一致性**：配色要服务于PPT的主题和受众

## 色彩心理学速查

| 颜色系 | 情绪联想 | 适用场景 |
|--------|---------|---------|
| 深蓝(Navy) | 专业、信任、稳重 | 商务汇报、金融、科技 |
| 翡翠绿(Emerald) | 成长、健康、自然 | 环保、医疗、教育 |
| 酒红(Burgundy) | 尊贵、经典、力量 | 品牌展示、奢侈品、传统行业 |
| 炭灰(Charcoal) | 现代、简约、高端 | 设计、互联网、极简风格 |
| 海洋蓝(Ocean) | 开放、创新、活力 | 互联网、创新、年轻化品牌 |
| 橙色 | 活力、行动、热情 | 营销、创业、行动号召 |
| 金色 | 品质、成就、价值 | 金融、高端品牌、成果展示 |

## 5套主题配色方案

前端已内置5套主题，AI输出的颜色值必须与所选主题一致：

### 主题1：深海蓝 (navy)
```
primary:     1F3864  (深蓝 - 主色，大面积色块)
primaryMid:  2E5090  (中蓝 - 辅助)
primaryLight:4472C4  (亮蓝 - 点缀)
accent:      D4560A  (橙红 - 强调条纹/高亮)
titleColor:  FFFFFF  (白色 - 深色区域标题)
bodyColor:   333333  (深灰 - 正文)
subColor:    666666  (中灰 - 副文字)
lightGray:   AAAAAA  (浅灰 - 辅助信息)
offWhite:    F2F4F7  (淡蓝灰 - 表格交替行)
```
**气质**：商务经典，适合企业汇报、金融分析

### 主题2：翡翠绿 (emerald)
```
primary:     1B5E3A  primaryMid:  2E7D50  primaryLight:4CAF6E
accent:      F9A825  titleColor:  FFFFFF
bodyColor:   2E3B2E  subColor:    5A6B5A  lightGray:   A0AEA0
offWhite:    F0F5F0
```
**气质**：自然成长，适合环保、教育、医疗

### 主题3：酒红金 (burgundy)
```
primary:     6B1D2A  primaryMid:  8E2D3E  primaryLight:B84056
accent:      C9A84C  titleColor:  FFFFFF
bodyColor:   3B2020  subColor:    6B4A4A  lightGray:   B09090
offWhite:    F7F0F0
```
**气质**：经典尊贵，适合品牌展示、传统行业

### 主题4：极简灰 (charcoal)
```
primary:     2C2C2C  primaryMid:  4A4A4A  primaryLight:6A6A6A
accent:      E85D3A  titleColor:  FFFFFF
bodyColor:   333333  subColor:    666666  lightGray:   AAAAAA
offWhite:    F5F5F5
```
**气质**：现代极简，适合设计、互联网、科技

### 主题5：海洋蓝 (ocean)
```
primary:     0D47A1  primaryMid:  1565C0  primaryLight:42A5F5
accent:      FF6D00  titleColor:  FFFFFF
bodyColor:   263238  subColor:    546E7A  lightGray:   90A4AE
offWhite:    E3F2FD
```
**气质**：活力创新，适合互联网、创业、年轻品牌

## 配色应用规则

### 在主色区域（头栏/封面色块）的文字
- **必须用 FFFFFF（白色）**，这是所有5套主题的通用规则
- 不要用其他颜色——深色背景上只有白色才够清晰

### 在白色区域的文字层次
```
标题/重要内容 → 333333（深灰）或 bodyColor
副标题/说明   → 666666（中灰）或 subColor  
辅助信息      → AAAAAA（浅灰）或 lightGray
```

### 强调色使用
- accent 颜色**只用于**：条纹装饰、重点标注、关键数字
- **不要大面积使用** accent —— 它是点缀，不是主色
- 表格表头用 primary，不要用 accent

### 禁止事项
1. ❌ 不要在AI输出中使用 `#` 前缀——颜色值是6位十六进制纯数字
2. ❌ 不要混用不同主题的颜色——一份PPT只用一套主题
3. ❌ 不要用纯黑 `000000`——太硬，用 `333333` 或 bodyColor 替代
4. ❌ 不要用饱和度极高的颜色作大面积背景——刺眼且不专业
