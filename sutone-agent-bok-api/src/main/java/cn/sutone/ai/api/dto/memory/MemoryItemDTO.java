package cn.sutone.ai.api.dto.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryItemDTO {

    private Long id;
    private String type;
    private String content;
    private Double score;
    private Double importance;
    private Integer accessCount;
    private String createTime;
}
