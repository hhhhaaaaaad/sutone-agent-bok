package cn.sutone.ai.domain.demo.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Demo 记录实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoRecordEntity {

    private Long recordId;
    private Long demoId;
    private String recordValue;
    private LocalDateTime createTime;

    public static DemoRecordEntity init(Long recordId, Long demoId, String recordValue) {
        return DemoRecordEntity.builder()
                .recordId(recordId)
                .demoId(demoId)
                .recordValue(recordValue)
                .createTime(LocalDateTime.now())
                .build();
    }
}
