package cn.sutone.ai.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryRecordPO {

    private Long id;
    private Long userId;
    private String type;
    private String content;
    private String contentHash;
    private String contentTokenized;
    private String sourceSessionId;
    private Double importance;
    private Integer accessCount;
    private LocalDateTime lastAccessedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
    private Double matchScore;
    private String vectorStatus;
    private Integer retryCount;
}
