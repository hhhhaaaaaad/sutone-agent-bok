package cn.sutone.ai.domain.demo.adapter.repository;

import cn.sutone.ai.domain.demo.model.entity.DemoEntity;
import cn.sutone.ai.domain.demo.model.entity.DemoRecordEntity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Demo 内存仓储实现
 */
public class InMemoryDemoRepository implements IDemoRepository {

    private final AtomicLong demoIdGenerator = new AtomicLong(1000L);
    private final AtomicLong recordIdGenerator = new AtomicLong(5000L);

    private final Map<Long, DemoEntity> demoStorage = new ConcurrentHashMap<>();
    private final Map<Long, DemoRecordEntity> recordStorage = new ConcurrentHashMap<>();

    @Override
    public Long nextDemoId() {
        return demoIdGenerator.incrementAndGet();
    }

    @Override
    public Long nextRecordId() {
        return recordIdGenerator.incrementAndGet();
    }

    @Override
    public void saveDemo(DemoEntity demoEntity) {
        demoStorage.put(demoEntity.getId(), demoEntity);
    }

    @Override
    public DemoEntity queryDemoById(Long id) {
        return demoStorage.get(id);
    }

    @Override
    public void saveRecord(DemoRecordEntity demoRecordEntity) {
        recordStorage.put(demoRecordEntity.getRecordId(), demoRecordEntity);
    }

    @Override
    public DemoRecordEntity queryRecordById(Long recordId) {
        return recordStorage.get(recordId);
    }
}
