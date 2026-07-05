package cn.sutone.ai.domain.demo.adapter.repository;

import cn.sutone.ai.domain.demo.model.entity.DemoEntity;
import cn.sutone.ai.domain.demo.model.entity.DemoRecordEntity;

/**
 * Demo 仓储接口
 */
public interface IDemoRepository {

    Long nextDemoId();

    Long nextRecordId();

    void saveDemo(DemoEntity demoEntity);

    DemoEntity queryDemoById(Long id);

    void saveRecord(DemoRecordEntity demoRecordEntity);

    DemoRecordEntity queryRecordById(Long recordId);
}
