package cn.sutone.ai.domain.demo.model.aggregate;

import cn.sutone.ai.domain.demo.model.entity.DemoEntity;
import cn.sutone.ai.domain.demo.model.entity.DemoRecordEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Demo 聚合
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoAggregate {

    private DemoEntity demoEntity;
    private DemoRecordEntity demoRecordEntity;

    public DemoRecordEntity createRecord(Long recordId, String recordValue) {
        this.demoEntity.enable();
        this.demoRecordEntity = DemoRecordEntity.init(recordId, demoEntity.getId(), recordValue);
        return this.demoRecordEntity;
    }
}
