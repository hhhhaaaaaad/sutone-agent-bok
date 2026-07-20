#!/usr/bin/env python3
"""
记忆系统性能压测 — 数据生成脚本
用法: python3 generate-memory-data.py --count 1000 --user-id 9999

流程：
1. 直接 INSERT INTO memory_record (批量，快速)
2. 调用 /api/v1/memory/migrate/all 同步向量到 V1(内存) 或 V2(Qdrant)
"""
import json
import hashlib
import sys
import time
import random
import argparse
import urllib.request
import urllib.parse
import http.cookiejar

API_BASE = "http://localhost:8091/api/v1"
PERF_USER_ID = 5

# 技术话题种子词，组合生成多样化记忆内容
TECH_TOPICS = [
    "Java", "Spring", "MySQL", "Redis", "Kafka", "Docker", "Qdrant", "HNSW",
    "JVM", "GC", "微服务", "DDD", "设计模式", "线程池", "分布式锁", "消息队列",
    "API", "JWT", "OAuth2", "负载均衡", "CI/CD", "Git", "代码审查", "单元测试",
    "性能优化", "缓存策略", "数据库索引", "B+树", "React", "TypeScript", "Next.js",
    "RESTful", "GraphQL", "gRPC", "Prometheus", "Grafana", "ELK", "日志收集",
    "正则表达式", "并发编程", "异步", "事件驱动", "Lambda", "Stream API",
    "HashMap", "ConcurrentHashMap", "ThreadLocal", "Netty", "Nginx",
    "Elasticsearch", "MongoDB", "PostgreSQL", "SQL优化", "慢查询分析"
]

VERBS = [
    "熟悉", "了解", "掌握", "偏好使用", "正在学习", "实践过", "深入研究过",
    "在实际项目中应用了", "最近调研了", "擅长"
]

CONTEXTS = [
    "在微服务项目中", "在性能优化时", "在代码重构时", "在面试准备中",
    "在技术选型时", "在日常开发中", "在架构设计中", "在排查线上问题时"
]

SUPPLEMENTS = [
    "，能够独立完成相关模块的设计与实现",
    "，具备生产环境的实战经验和问题排查能力",
    "，对底层原理有较深的理解",
    "，并在团队内做过技术分享",
    "，有过大规模数据场景下的调优实践",
    "，结合业务场景做过深入的技术选型对比",
    "，写过相关的技术博客总结经验",
    "，参与过相关组件的源码阅读和贡献",
    "",
    "",
]


def md5(text: str) -> str:
    return hashlib.md5(text.encode('utf-8')).hexdigest()


def generate_content() -> str:
    """生成一条随机的技术记忆内容（25-60字）"""
    topic = random.choice(TECH_TOPICS)
    verb = random.choice(VERBS)
    context = random.choice(CONTEXTS)
    supplement = random.choice(SUPPLEMENTS)
    return f"{context}{verb}{topic}{supplement}"


def generate_memories(count: int) -> list[dict]:
    """生成 N 条记忆数据"""
    types = ["fact", "preference", "knowledge", "event"]
    memories = []
    seen_hashes = set()

    retries = 0
    while len(memories) < count and retries < count * 10:
        content = generate_content()
        h = md5(content)
        if h in seen_hashes:
            retries += 1
            continue
        seen_hashes.add(h)

        memories.append({
            "id": None,  # AUTO_INCREMENT
            "user_id": PERF_USER_ID,
            "type": random.choice(types),
            "content": content,
            "content_hash": h,
            "source_session_id": "perf",
            "importance": round(random.uniform(0.3, 0.9), 2),
            "vector_status": "PENDING",
            "retry_count": 0
        })
        retries = 0

    return memories


def insert_mysql(memories: list[dict]) -> int:
    """通过 MySQL CLI 批量插入记录"""
    import subprocess

    # Build SQL
    lines = []
    lines.append("USE sutone_agent_bok;")
    for m in memories:
        content_escaped = m["content"].replace("'", "\\'")
        lines.append(
            f"INSERT INTO memory_record "
            f"(user_id, type, content, content_hash, source_session_id, "
            f"importance, access_count, last_accessed_at, create_time, update_time, "
            f"is_deleted, vector_status, retry_count) "
            f"VALUES ("
            f"{m['user_id']}, '{m['type']}', '{content_escaped}', '{m['content_hash']}', "
            f"'{m['source_session_id']}', {m['importance']}, 0, NOW(), NOW(), NOW(), "
            f"0, '{m['vector_status']}', {m['retry_count']}"
            f");"
        )

    sql = "\n".join(lines)

    # Execute via docker exec
    cmd = [
        "docker", "exec", "-i", "mysql",
        "mysql", "-uroot", "-p123456"
    ]

    result = subprocess.run(cmd, input=sql.encode('utf-8'),
                            capture_output=True, timeout=120)
    if result.returncode != 0:
        stderr = result.stderr.decode('utf-8', errors='replace')
        if "Duplicate entry" in stderr:
            # 去重冲突不算失败
            pass
        elif stderr.strip():
            print(f"  MySQL warning: {stderr[:200]}", file=sys.stderr)

    return len(memories)


def migrate_vectors(batch_size: int = 50, rate_limit: int = 20) -> dict:
    """调用 /api/v1/memory/migrate/all 同步向量"""
    # migrate/all 是 permitAll 的，不需要 auth
    params = urllib.parse.urlencode({
        "batchSize": batch_size,
        "rateLimit": rate_limit
    })
    url = f"{API_BASE}/memory/migrate/all?{params}"
    req = urllib.request.Request(url, method="POST")

    try:
        with urllib.request.urlopen(req, timeout=1200) as resp:
            return json.loads(resp.read().decode('utf-8'))
    except Exception as e:
        print(f"  migrate error: {e}", file=sys.stderr)
        return {"error": str(e)}


def cleanup_mysql(user_id: int):
    """清理旧测试数据"""
    import subprocess
    sql = f"DELETE FROM memory_record WHERE user_id = {user_id} AND source_session_id = 'perf';"
    cmd = ["docker", "exec", "-i", "mysql", "mysql", "-uroot", "-p123456", "sutone_agent_bok"]
    subprocess.run(cmd, input=sql.encode('utf-8'), capture_output=True, timeout=30)


def cleanup_qdrant(user_id: int):
    """清理 Qdrant 中的测试向量"""
    payload = json.dumps({
        "filter": {
            "must": [
                {"key": "user_id", "match": {"value": user_id}},
                {"key": "source", "match": {"value": "perf"}}
            ]
        }
    }).encode('utf-8')

    url = f"http://localhost:6333/collections/agent_memory/points/delete"
    req = urllib.request.Request(url, data=payload, method="POST",
                                  headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read().decode('utf-8'))
    except Exception as e:
        print(f"  Qdrant cleanup note: {e}", file=sys.stderr)


def flush_redis():
    """清空 Redis 缓存"""
    import subprocess
    result = subprocess.run(
        ["docker", "exec", "redis", "redis-cli", "FLUSHDB"],
        capture_output=True, timeout=10
    )
    if result.returncode == 0:
        print("  Redis FLUSHDB OK", flush=True)
    else:
        print(f"  Redis FLUSHDB failed: {result.stderr.decode()}", file=sys.stderr)


def verify_count(expected: int) -> bool:
    """验证 MySQL 中实际插入的记录数"""
    import subprocess
    sql = f"SELECT COUNT(*) FROM memory_record WHERE user_id = {PERF_USER_ID} AND source_session_id = 'perf' AND is_deleted = 0;"
    cmd = ["docker", "exec", "-i", "mysql", "mysql", "-uroot", "-p123456", "-N", "sutone_agent_bok"]
    result = subprocess.run(cmd, input=sql.encode('utf-8'),
                            capture_output=True, timeout=10)
    count = int(result.stdout.decode('utf-8').strip() or 0)
    return count >= expected


def main():
    parser = argparse.ArgumentParser(description="记忆系统性能测试数据生成器")
    parser.add_argument("--count", type=int, default=100, help="生成记忆条数 (default: 100)")
    parser.add_argument("--user-id", type=int, default=PERF_USER_ID, help="关联用户ID")
    parser.add_argument("--clean", action="store_true", help="清理旧数据后重新生成")
    parser.add_argument("--skip-migrate", action="store_true", help="仅插入MySQL,不迁移向量")
    parser.add_argument("--flush-redis", action="store_true", help="执行 Redis FLUSHDB")
    args = parser.parse_args()

    print(f"=== 生成 {args.count} 条性能测试记忆 (user_id={args.user_id}) ===", flush=True)

    if args.flush_redis:
        flush_redis()

    if args.clean:
        print("[1/4] 清理旧数据...", flush=True)
        cleanup_mysql(args.user_id)
        cleanup_qdrant(args.user_id)
        time.sleep(2)
    else:
        print("[1/4] 跳过清理 (--clean 可强制清理)", flush=True)

    print(f"[2/4] 生成 {args.count} 条随机记忆...", flush=True)
    t0 = time.time()
    memories = generate_memories(args.count)
    print(f"      生成 {len(memories)} 条, 耗时 {time.time()-t0:.1f}s", flush=True)

    print(f"[3/4] 批量插入 MySQL...", flush=True)
    t0 = time.time()
    insert_mysql(memories)
    print(f"      插入完成, 耗时 {time.time()-t0:.1f}s", flush=True)

    if not verify_count(args.count):
        actual = args.count  # fallback
        print(f"      警告: 实际插入数量可能与预期不符", flush=True)

    if not args.skip_migrate:
        print(f"[4/4] 同步向量 (migrate/all)...", flush=True)
        t0 = time.time()
        result = migrate_vectors(batch_size=50, rate_limit=20)
        elapsed = time.time() - t0
        print(f"      迁移结果: {json.dumps(result, ensure_ascii=False)[:200]}", flush=True)
        print(f"      耗时 {elapsed:.1f}s ({args.count/elapsed:.1f} 条/s)", flush=True)
    else:
        print("[4/4] 跳过向量迁移 (--skip-migrate)", flush=True)

    print(f"\n=== 完成! 共 {args.count} 条记忆 (user_id={args.user_id}) ===", flush=True)


if __name__ == "__main__":
    main()
