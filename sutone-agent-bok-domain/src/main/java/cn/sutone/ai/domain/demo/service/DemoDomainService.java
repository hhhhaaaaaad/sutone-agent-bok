package cn.sutone.ai.domain.demo.service;

import cn.sutone.ai.domain.demo.adapter.repository.IDemoRepository;
import cn.sutone.ai.domain.demo.model.aggregate.DemoAggregate;
import cn.sutone.ai.domain.demo.model.entity.DemoEntity;
import cn.sutone.ai.domain.demo.model.entity.DemoRecordEntity;

/**
 * Demo 领域服务
 */
public class DemoDomainService {

    private final IDemoRepository demoRepository;

    public DemoDomainService(IDemoRepository demoRepository) {
        this.demoRepository = demoRepository;
    }

    public DemoEntity createDemo(String demoCode, String demoName) {
        Long demoId = demoRepository.nextDemoId();
        DemoEntity demoEntity = DemoEntity.init(demoId, demoCode, demoName);
        demoRepository.saveDemo(demoEntity);
        return demoEntity;
    }

    public DemoEntity renameDemo(Long demoId, String demoName) {
        DemoEntity demoEntity = demoRepository.queryDemoById(demoId);
        demoEntity.rename(demoName);
        demoRepository.saveDemo(demoEntity);
        return demoEntity;
    }

    public DemoRecordEntity createRecord(Long demoId, String recordValue) {
        DemoEntity demoEntity = demoRepository.queryDemoById(demoId);
        Long recordId = demoRepository.nextRecordId();

        DemoAggregate demoAggregate = DemoAggregate.builder()
                .demoEntity(demoEntity)
                .build();

        DemoRecordEntity demoRecordEntity = demoAggregate.createRecord(recordId, recordValue);
        demoRepository.saveDemo(demoAggregate.getDemoEntity());
        demoRepository.saveRecord(demoRecordEntity);
        return demoRecordEntity;
    }
}
