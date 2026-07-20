#!/bin/bash
# 记忆系统搜索接口性能压测 v2
# 用法: ./run-perf.sh --mode v2 --counts "100,500,1000"
set -e

JMETER_HOME="/tmp/apache-jmeter-5.6.3"
JMETER_BIN="$JMETER_HOME/bin/jmeter"
JAVA_HOME="/Users/suke/.local/jdks/jdk-21.0.11+10/Contents/Home"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PERF_USER_ID=5
RESULTS_DIR="$SCRIPT_DIR/results"
ROUNDS=3

MODE="v2"
COUNTS="100,500,1000"
THREADS=20
LOOPS=10
RAMP_UP=5

while [[ $# -gt 0 ]]; do
    case "$1" in
        --mode)   MODE="$2"; shift 2 ;;
        --counts) COUNTS="$2"; shift 2 ;;
        --threads) THREADS="$2"; shift 2 ;;
        --loops)  LOOPS="$2"; shift 2 ;;
        *)        echo "未知参数: $1"; exit 1 ;;
    esac
done

mkdir -p "$RESULTS_DIR"
export JAVA_HOME="$JAVA_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

echo "======================================================"
echo "  记忆系统性能压测 v2"
echo "  模式: $MODE | 数据量: $COUNTS"
echo "  并发: ${THREADS} | 循环/线程: ${LOOPS} | 轮次: ${ROUNDS}"
echo "======================================================"

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8091/api/v1/auth/login \
    -X POST -H "Content-Type: application/json" \
    -d '{"username":"testuser","password":"123456"}' --max-time 5)
[ "$HTTP_CODE" != "200" ] && echo "ERROR: 应用未启动" && exit 1
echo "[Check] 应用 OK, JMeter OK"

JAVA_PID=$(ps aux | grep "[j]ava.*sutone-agent-bok" | awk '{print $2}' | head -1)

# ---- 预热 ----
warmup() {
    curl -s -c /tmp/perf-cookies.txt -X POST http://localhost:8091/api/v1/auth/login \
        -H "Content-Type: application/json" \
        -d '{"username":"testuser","password":"123456"}' > /dev/null
    echo -n "  [Warmup] "
    for i in $(seq 1 50); do
        Q=$(tail -n +2 "$SCRIPT_DIR/search-queries.csv" | sort -R | head -1)
        Q_ENCODED=$(python3 -c "import urllib.parse; print(urllib.parse.quote('$Q'))")
        curl -s -b /tmp/perf-cookies.txt \
            "http://localhost:8091/api/v1/memory/search?q=${Q_ENCODED}&n=5" > /dev/null
        [ $((i % 10)) -eq 0 ] && echo -n "."
    done
    echo " Done"
}

# ---- JTL 分析 ----
analyze_jtl() {
    local JTL="$1" LBL="$2" CNT="$3" RND="$4"
    python3 - "$JTL" "$LBL" "$CNT" "$RND" << 'PYEOF'
import sys, csv, statistics, json
jtl_file, label, count, rnd = sys.argv[1:]

elapsed = []; timestamps = []; total = 0; errors = 0
with open(jtl_file) as f:
    for row in csv.DictReader(f):
        if 'Login' in row.get('label',''): continue
        total += 1
        if row.get('success') == 'true':
            elapsed.append(int(row['elapsed']))
            timestamps.append(int(row['timeStamp']))
        else:
            errors += 1

if not elapsed:
    print("  [WARN] No valid samples"); sys.exit(0)

elapsed.sort(); n = len(elapsed)
first_ts = min(timestamps); last_ts = max(timestamps)
duration_s = (last_ts - first_ts) / 1000
tps = n / duration_s if duration_s > 0 else 0
p50 = elapsed[n//2]
p90 = elapsed[int(n*0.9)]
p99 = elapsed[min(int(n*0.99), n-1)]
avg = statistics.mean(elapsed)

print(f"  R{rnd}: TPS={tps:.1f} | Avg={avg:.0f}ms | P50={p50} | P90={p90} | P99={p99} | Err={errors}/{total}")

summary = {"mode":label,"count":int(count),"round":int(rnd),
    "samples":total,"errors":errors,"tps":round(tps,1),"avg_ms":round(avg,0),
    "p50_ms":p50,"p90_ms":p90,"p99_ms":p99,"min_ms":min(elapsed),"max_ms":max(elapsed),
    "duration_s":round(duration_s,2)}

with open(jtl_file.replace('.jtl','-summary.json'), 'w') as f:
    json.dump(summary, f, indent=2)
PYEOF
}

# ---- 主函数 ----
run_for_mode() {
    local CM="$1" ML="$2" TS=$(date +%Y%m%d-%H%M%S)
    echo ""; echo "===== $ML ($CM) ====="

    IFS=',' read -ra CA <<< "$COUNTS"
    for COUNT in "${CA[@]}"; do
        COUNT=$(echo "$COUNT" | xargs)
        echo ""; echo "--- N=$COUNT ---"

        echo "  [1] 清理 + 生成数据..."
        python3 "$SCRIPT_DIR/generate-memory-data.py" --count "$COUNT" --user-id "$PERF_USER_ID" --clean 2>&1 | grep -E "完成|迁移结果|耗时|Redis" || true
        sleep 3

        warmup
        docker exec redis redis-cli FLUSHDB > /dev/null 2>&1 || true

        # GC before
        [ -n "$JAVA_PID" ] && "$JAVA_HOME/bin/jstat" -gcutil "$JAVA_PID" 1000 1 2>/dev/null | head -2 || true

        for ROUND in $(seq 1 "$ROUNDS"); do
            docker exec redis redis-cli FLUSHDB > /dev/null 2>&1 || true
            sleep 1

            RF="$RESULTS_DIR/perf-${CM}-n${COUNT}-t${THREADS}-r${ROUND}-${TS}"
            rm -rf "$RF-report" 2>/dev/null

            "$JMETER_BIN" -n -t "$SCRIPT_DIR/memory-search-stress.jmx" \
                -Jthreads="$THREADS" -JrampUp="$RAMP_UP" -Jloops="$LOOPS" \
                -Jhost="localhost" -Jport="8091" \
                -JCSV_PATH="$SCRIPT_DIR/search-queries.csv" \
                -l "$RF.jtl" 2>&1 | grep "summary =" || true

            analyze_jtl "$RF.jtl" "$ML" "$COUNT" "$ROUND"
        done

        # GC after
        [ -n "$JAVA_PID" ] && "$JAVA_HOME/bin/jstat" -gcutil "$JAVA_PID" 1000 1 2>/dev/null | head -2 || true

        # Median across rounds
        python3 - "$RESULTS_DIR" "$CM" "$COUNT" "$THREADS" "$TS" "$ROUNDS" << 'PYEOF'
import sys,json,glob,statistics
rd,mode,cnt,thr,ts,rds=sys.argv[1:]
fs=sorted(glob.glob(f"{rd}/perf-{mode}-n{cnt}-t{thr}-r*-{ts}-summary.json"))
if not fs: sys.exit(0)
tps_l=[json.load(open(f))['tps'] for f in fs]
p99_l=[json.load(open(f))['p99_ms'] for f in fs]
p50_l=[json.load(open(f))['p50_ms'] for f in fs]
p90_l=[json.load(open(f))['p90_ms'] for f in fs]
print(f"  MEDIAN: TPS={statistics.median(tps_l):.1f} P50={int(statistics.median(p50_l))} P90={int(statistics.median(p90_l))} P99={int(statistics.median(p99_l))}")
json.dump({"mode":mode,"count":int(cnt),"threads":int(thr),"rounds":int(rds),
    "median_tps":statistics.median(tps_l),"median_p50":int(statistics.median(p50_l)),
    "median_p90":int(statistics.median(p90_l)),"median_p99":int(statistics.median(p99_l))},
    open(f"{rd}/final-{mode}-n{cnt}-t{thr}.json","w"),indent=2)
PYEOF
    done
}

case "$MODE" in
    v1) run_for_mode "v1" "V1-ConcurrentHashMap" ;;
    v2) run_for_mode "v2" "V2-QdrantHNSW" ;;
    both)
        echo; echo "  >>> 请切换到 V1 (memory.vector-store=memory) 并重启, 按 Enter"; read -r
        run_for_mode "v1" "V1-ConcurrentHashMap"
        echo; echo "  >>> 请切换到 V2 (memory.vector-store=qdrant) 并重启, 按 Enter"; read -r
        run_for_mode "v2" "V2-QdrantHNSW"
        echo; echo "=== V1 vs V2 ==="
        python3 - "$RESULTS_DIR" << 'PYEOF'
import sys,json,glob
rd=sys.argv[1]
for v1f,v2f in zip(sorted(glob.glob(f"{rd}/final-v1-*.json")),sorted(glob.glob(f"{rd}/final-v2-*.json"))):
    v1=json.load(open(v1f)); v2=json.load(open(v2f))
    r=v2['median_tps']/v1['median_tps'] if v1['median_tps']>0 else 0
    print(f"  N={v1['count']}: V1 TPS={v1['median_tps']:.1f} V2 TPS={v2['median_tps']:.1f} ({r:.1f}x) | V1 P99={v1['median_p99']} V2 P99={v2['median_p99']}")
PYEOF
        ;;
    *) echo "未知模式: $MODE"; exit 1 ;;
esac
echo ""; echo "=== Done ==="; echo "Results: $RESULTS_DIR"
