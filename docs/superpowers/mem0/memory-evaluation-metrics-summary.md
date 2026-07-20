# 记忆系统测试指标汇总

本文档汇总 Mem0 记忆系统建议测试和跟踪的核心指标。指标来源于现有评估文档、benchmark 代码和运行时检索逻辑，适用于评估 Mem0 Platform、Mem0 OSS 或不同模型/检索配置之间的效果差异。

## 评估目标

记忆系统的评估不应只看单一准确率。当前项目里强调的核心目标是同时平衡三类结果：

- **准确性**：检索到的记忆是否能支撑正确回答。
- **成本**：每次查询消耗多少上下文 token，是否具备 token efficiency。
- **性能**：写入、检索、生成和评判链路的延迟是否可接受。

建议所有评估在固定变量下进行，只改变一个待测试配置，例如模型、embedding、top-k、rerank、后端类型或检索策略。

## 必测指标

| 指标 | 含义 | 建议统计方式 | 对应代码/文档 |
| --- | --- | --- | --- |
| Overall Accuracy | 整体通过率，表示回答被判定为正确的比例 | `correct / total * 100` | `evaluation/benchmarks/common/metrics.py` |
| Average Score | 平均评分，适合 BEAM 这类非二元评分任务 | 所有样本 `score` 的均值 | `evaluation/benchmarks/common/metrics.py` |
| Pass Rate | 达到通过阈值的比例 | 默认 `score >= 0.5` 判为通过 | `evaluation/benchmarks/common/metrics.py` |
| Error Count | 评估过程中失败或 judge 返回错误的样本数 | 统计 `judgment == ERROR` 或存在 `error` 的样本 | `evaluation/benchmarks/common/metrics.py` |
| Search Latency | 单次记忆检索耗时 | `search_latency_ms`，建议统计 avg/p50/p95 | `evaluation/benchmarks/common/schema.py` |
| Add Latency | 记忆写入或抽取链路耗时 | `add_latency_total_ms`，建议统计 avg/p50/p95 | `evaluation/src/lib/adapters.ts` |
| Average Tokens per Query | 每次查询注入给回答模型的记忆上下文 token 数 | 按 query 求均值；当前文档强调约 7K token 级别 | `docs/core-concepts/memory-evaluation.mdx` |
| Top-k Cutoff Accuracy | 不同检索深度下的准确率 | 对 `top_10/top_20/top_50/top_200` 分别计算 | `evaluation/benchmarks/common/metrics.py` |
| By-group Accuracy | 不同问题类型/能力类别的准确率 | 按 `category_name` 或 `question_type` 分组 | `evaluation/benchmarks/common/metrics.py` |
| Kendall Tau-b | 事件排序题的排序一致性 | BEAM event_ordering 专用，范围 `[-1, 1]` | `evaluation/benchmarks/common/metrics.py` |

## 按能力维度测试

### 基础记忆召回

用于确认系统能否从历史对话中找回明确事实。

- **single-hop**：单条记忆即可回答的问题。
- **multi-hop**：需要组合多条记忆的问题。
- **open-domain**：更开放、边界不明确的事实召回。
- **information_extraction**：能否抽取并使用细粒度信息。

适用 benchmark：LoCoMo、BEAM。

### 用户和助手记忆

用于确认系统是否能区分并保留不同来源的信息。

- **single-session-user**：用户在单会话中表达的信息。
- **single-session-assistant**：助手确认、生成或承诺的信息。
- **single-session-preference**：用户偏好。
- **preference_following**：回答是否遵守长期偏好。
- **instruction_following**：回答是否遵守长期指令。

适用 benchmark：LongMemEval、BEAM。

### 时间和状态变化

用于确认 ADD-only 记忆架构是否能处理旧事实、新事实和时间语义。

- **knowledge_update**：新事实出现后，系统是否能优先使用当前正确事实。
- **temporal_reasoning**：能否理解过去、当前、未来、具体日期等时间约束。
- **event_ordering**：能否恢复事件发生顺序。
- **contradiction_resolution**：面对冲突记忆时能否选择正确结论。

适用 benchmark：LongMemEval、BEAM。

### 大规模上下文能力

用于确认系统在生产级上下文规模下是否仍然有效。

- **1M token scale**：中大规模长期记忆评估。
- **10M token scale**：更接近生产长期使用场景的压力评估。
- **multi_session_reasoning**：跨多轮、多会话聚合推理。
- **summarization**：从大量记忆中归纳总结。
- **abstention**：没有证据时能否拒答或避免编造。

适用 benchmark：BEAM。

## 检索质量指标

| 指标 | 含义 | 测试重点 |
| --- | --- | --- |
| Semantic Score | 向量语义相似度得分 | 概念类问题是否能召回相关记忆 |
| BM25 Score | 关键词匹配得分 | 精确词、名称、日期、任务编号等是否被召回 |
| Entity Boost | 实体匹配带来的加权 | 人名、项目名、地点、组织等实体相关查询 |
| Combined Score | 多信号融合后的最终分数 | semantic、BM25、entity 等信号融合是否稳定 |
| Final Rank | 记忆在结果中的最终排序 | 正确记忆是否排在靠前位置 |
| Total Results | 返回候选记忆数量 | top-k、threshold 是否导致召回不足或噪音过多 |

相关实现：

- `mem0/utils/scoring.py`：组合打分和 `score_details`。
- `evaluation/src/lib/schema.ts`：`ScoreDebug` 和 `QueryDebug` 前端结构。
- `tests/utils/test_scoring.py`：BM25、组合打分和 score details 的单测。

## 性能和成本指标

| 指标 | 含义 | 建议阈值/观察方式 |
| --- | --- | --- |
| Search Latency avg/p50/p95 | 检索耗时分布 | 项目运行时慢查询阈值为 2.0s |
| Add Latency avg/p50/p95 | 写入、抽取、embedding、实体链接耗时 | 区分同步写入和异步后处理耗时 |
| Generation Latency | 从检索记忆生成答案的耗时 | 与回答模型固定绑定比较 |
| Judgment Latency | judge LLM 评估耗时 | 用于估算 benchmark 总成本和运行时间 |
| Tokens per Query | 每次查询给 answerer 的上下文 token | 与 full-context 方案比较 token efficiency |
| Top-k Budget | 每次检索允许返回的记忆数量 | 默认重点比较 `top_50` 和 `top_200` |
| Rerank Cost | rerank 带来的延迟和准确率变化 | 做 A/B 对比，确认收益大于额外耗时 |

相关实现：

- `mem0/memory/main.py`：`search()` 内部统计 `search_elapsed_seconds`。
- `mem0/memory/notices.py`：慢查询阈值 `PERFORMANCE_SLOW_QUERY_THRESHOLD_SECONDS = 2.0`。
- `evaluation/src/lib/adapters.ts`：计算 `avg_search_latency_ms` 和 `avg_add_latency_ms`。

## 推荐测试矩阵

### 基线测试

- 固定后端：`cloud` 或 `oss`。
- 固定 answerer model、judge model、embedding model。
- 固定 `top-k = 200`。
- 跑 LoCoMo、LongMemEval、BEAM 1M，记录 accuracy、avg_score、tokens/query、latency。

### Top-k 敏感性测试

- 对比 `top_10`、`top_20`、`top_50`、`top_200`。
- 观察 accuracy 是否随着 top-k 增大提升。
- 观察 token 成本和延迟是否线性增长。
- 判断最划算的 retrieval budget。

### 检索策略 A/B 测试

- semantic only vs semantic + BM25。
- semantic + BM25 vs semantic + BM25 + entity boost。
- rerank off vs rerank on。
- temporal reasoning off vs on，如果配置支持。

### 模型配置对比

- 不同 extraction LLM。
- 不同 embedding model。
- 不同 answerer model。
- 不同 judge model。

比较时必须保持其他变量不变，否则 benchmark 分数不可直接归因。

### 大规模压力测试

- BEAM 1M：验证中大规模长期记忆效果。
- BEAM 10M：验证生产级长期记忆压力下的效果。
- 重点关注 temporal_reasoning、event_ordering、multi_session_reasoning、contradiction_resolution，因为这些是长上下文下最容易退化的能力。

## 推荐输出格式

每次评估建议保留以下字段，方便横向比较：

```json
{
  "benchmark": "locomo | longmemeval | beam",
  "backend": "cloud | oss",
  "top_k": 200,
  "top_k_cutoffs": [10, 20, 50, 200],
  "answerer_model": "...",
  "judge_model": "...",
  "embedding_model": "...",
  "overall_accuracy": 0.0,
  "overall_avg_score": 0.0,
  "total": 0,
  "correct": 0,
  "errors": 0,
  "avg_search_latency_ms": 0.0,
  "avg_add_latency_ms": 0.0,
  "avg_tokens_per_query": 0.0,
  "by_group": {},
  "by_cutoff": {}
}
```

## 当前项目中的参考入口

- `docs/core-concepts/memory-evaluation.mdx`：记忆系统评估理念、benchmark 结果、运行方式。
- `evaluation/README.md`：benchmark pipeline、命令参数、结果示例。
- `evaluation/benchmarks/common/schema.py`：统一结果 schema。
- `evaluation/benchmarks/common/metrics.py`：核心指标计算。
- `evaluation/src/lib/schema.ts`：前端展示使用的指标结构。
- `evaluation/src/lib/adapters.ts`：原始结果到统一指标的转换。
- `mem0/memory/main.py`：运行时搜索耗时统计。
- `mem0/memory/notices.py`：慢查询阈值和 notice 逻辑。
- `mem0/utils/scoring.py`：检索多信号打分明细。

## 优先级建议

如果只做最小可用评估，优先测试：

1. Overall Accuracy
2. Average Score / Pass Rate
3. By-group Accuracy
4. Top-k Cutoff Accuracy
5. Search Latency p50/p95
6. Average Tokens per Query
7. Error Count

如果要做生产级评估，再补充：

1. Add Latency p50/p95
2. Rerank latency vs accuracy gain
3. BEAM 1M/10M 大规模结果
4. Temporal reasoning、event ordering、contradiction resolution 分项
5. ScoreDebug 中 semantic、BM25、entity boost 的排序贡献
