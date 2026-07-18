package cn.sutone.ai.types.dto.comment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentPageResponseDTO {
    private List<CommentItemResponseDTO> list;
    private int total;
    private int page;
    private int pageSize;
}
