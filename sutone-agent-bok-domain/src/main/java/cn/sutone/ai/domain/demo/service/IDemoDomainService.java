package cn.sutone.ai.domain.demo.service;

import cn.sutone.ai.domain.demo.model.entity.DemoEntity;
import cn.sutone.ai.domain.demo.model.entity.DemoRecordEntity;

/**
 * Demo 领域服务接口
 */
public interface IDemoDomainService {

    DemoEntity createDemo(String demoCode, String demoName);

    DemoEntity renameDemo(Long demoId, String demoName);

    DemoRecordEntity createRecord(Long demoId, String recordValue);

}
