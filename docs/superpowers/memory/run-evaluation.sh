#!/bin/bash
# 记忆系统质量评测脚本（兼容 macOS bash 3.x）
# 用法：./run-evaluation.sh
set -e

API_BASE="http://localhost:8091/api/v1"
COOKIE_FILE="/tmp/eval-cookies.txt"
QDRANT="http://localhost:6333"

echo "=== 记忆系统质量评测 ==="

# Step 1: 登录
echo "[1/4] 登录..."
curl -s -c "$COOKIE_FILE" -X POST "${API_BASE}/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"123456"}' > /dev/null

# Step 2: 迁移到 Qdrant
echo "[2/4] 同步向量..."
MIGRATE=$(curl -s -X POST "${API_BASE}/memory/migrate/all?batchSize=50&rateLimit=10")
echo "  $MIGRATE"

# Step 3: 运行检索评测 + 计算指标 (Python)
echo "[3/4] 运行检索评测..."
python3 - "$COOKIE_FILE" "$API_BASE" << 'PYEOF'
import json, sys, urllib.request, urllib.parse, statistics, http.cookiejar

cookie_file = sys.argv[1]
base_url = sys.argv[2]

# Load cookies
cj = http.cookiejar.MozillaCookieJar(cookie_file)
cj.load()

opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))

# 20条查询定义
queries = [
    ("JVM 调优",       "semantic",    [1008,1016,1034]),
    ("Java开发工具",    "semantic",    [1004,1019]),
    ("缓存相关经验",    "multi-hop",   [1009,1017,1036]),
    ("数据库优化",      "semantic",    [1010,1044]),
    ("最近在做什么",    "temporal",    [1016,1017,1024,1030,1047]),
    ("写文章的风格偏好", "preference",  [1005,1006,1021]),
    ("分布式系统",      "multi-hop",   [1023,1039,1012,1043]),
    ("Spring相关技术",  "multi-hop",   [1002,1011,1028,1029]),
    ("容器部署",        "semantic",    [1013,1045,1048]),
    ("AI大模型",        "multi-hop",   [1015,1025,1026,1037,1042]),
    ("用户是做什么的",  "open-domain", [1001,1003,1018]),
    ("项目架构",        "multi-hop",   [1002,1020,1031,1037]),
    ("消息队列",        "single-hop",  [1012]),
    ("面试准备",        "open-domain", [1018,1042]),
    ("代码风格",        "preference",  [1033,1040,1007]),
    ("向量搜索",        "single-hop",  [1014,1026]),
    ("安全认证",        "semantic",    [1028,1046]),
    ("性能优化经验",    "multi-hop",   [1008,1009,1010,1024,1034]),
    ("前端技术",        "single-hop",  [1031,1032]),
    ("Git工作流",       "single-hop",  [1041]),
]

total_recall5 = 0
total_mrr = 0
total_hit1 = 0
group_data = {}
details = []

for i, (q, group, relevant_list) in enumerate(queries):
    relevant = set(str(r) for r in relevant_list)
    params = urllib.parse.urlencode({"q": q, "n": "5"})
    url = f"{base_url}/memory/search?{params}"
    req = urllib.request.Request(url)
    with opener.open(req) as resp:
        data = json.loads(resp.read().decode())

    items = data.get("data", {}).get("items", [])
    retrieved = [str(item.get("id", "")) for item in items]

    # Recall@5
    hits = len(relevant & set(retrieved))
    recall = hits / len(relevant) if relevant else 0
    total_recall5 += recall

    # MRR
    mrr = 0
    for j, rid in enumerate(retrieved):
        if rid in relevant:
            mrr = 1.0 / (j + 1)
            break
    total_mrr += mrr

    # Hit@1
    hit1 = 1 if (retrieved and retrieved[0] in relevant) else 0
    total_hit1 += hit1

    group_data.setdefault(group, []).append(recall)

    details.append({
        "idx": i+1, "group": group, "query": q,
        "relevant_n": len(relevant), "hits": hits,
        "recall": round(recall, 3), "mrr": round(mrr, 3),
        "hit_at_1": bool(hit1),
        "top_ids": retrieved[:5]
    })
    print(".", end="", flush=True)

N = len(queries)
print(" DONE\n")

avg_recall = total_recall5 / N
avg_mrr = total_mrr / N
avg_hit1 = total_hit1 / N

print("=" * 50)
print("  评测结果")
print("=" * 50)
print(f"  Recall@5 = {avg_recall:.3f}")
print(f"  MRR      = {avg_mrr:.3f}")
print(f"  Hit@1    = {avg_hit1:.3f}")

print("\n--- 按能力分组 ---")
group_labels = [
    ("single-hop",  "精确匹配"),
    ("semantic",    "语义泛化"),
    ("multi-hop",   "跨类型聚合"),
    ("preference",  "偏好召回"),
    ("temporal",    "时间敏感"),
    ("open-domain", "开放领域"),
]
for g_key, g_label in group_labels:
    if g_key in group_data:
        vals = group_data[g_key]
        avg = statistics.mean(vals) if vals else 0
        print(f"  {g_label:10s} Recall@5 = {avg:.3f} (n={len(vals)})")

# 生成 JSON 报告
report = {
    "eval_date": "2026-07-19",
    "config": {"corpus_size": 50, "query_count": N, "top_k": 5, "reranker_enabled": True},
    "results": {
        "recall_at_5": round(avg_recall, 3),
        "mrr": round(avg_mrr, 3),
        "hit_at_1": round(avg_hit1, 3),
        "by_group": {g_key: round(statistics.mean(vals), 3) for g_key, vals in group_data.items()}
    },
    "details": details
}

report_path = "docs/superpowers/memory/evaluation-report.json"
with open(report_path, 'w') as f:
    json.dump(report, f, ensure_ascii=False, indent=2)
print(f"\n  报告已保存: {report_path}")
PYEOF

echo ""
echo "[4/4] 评测完成！"
rm -f "$COOKIE_FILE"
