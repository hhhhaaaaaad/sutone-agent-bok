package cn.sutone.ai.api.dto.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryListResponseDTO {

    private List<MemoryItemDTO> items;
    private Integer page;
    private Integer pageSize;
    private Integer total;
}
