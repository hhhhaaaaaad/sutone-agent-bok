package cn.sutone.ai.domain.agent.model.entity;

import cn.sutone.ai.domain.agent.model.valobj.MemoryTypeVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryRecordEntity {

    private Long id;
    private Long userId;
    private MemoryTypeVO type;
    private String content;
    private String contentHash;
    private String contentTokenized;
    private String sourceSessionId;
    private Double importance;
    private Integer accessCount;
    private LocalDateTime lastAccessedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Double matchScore;

    public static MemoryRecordEntity create(Long id, Long userId, String type, String content,
                                            String contentHash, String sessionId) {
        return MemoryRecordEntity.builder()
                .id(id)
                .userId(userId)
                .type(MemoryTypeVO.fromCode(type))
                .content(content)
                .contentHash(contentHash)
                .sourceSessionId(sessionId)
                .importance(0.5)
                .accessCount(0)
                .build();
    }
}
