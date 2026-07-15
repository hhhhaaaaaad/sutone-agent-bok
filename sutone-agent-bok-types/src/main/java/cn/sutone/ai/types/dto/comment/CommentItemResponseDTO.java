package cn.sutone.ai.types.dto.comment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentItemResponseDTO {
    private Long commentId;
    private Long articleId;
    private Long authorId;
    private String authorName;
    private String avatarUrl;
    private String content;
    private Long parentId;
    private Integer likeCount;
    private Boolean liked;
    private String createTime;
    @Builder.Default
    private List<CommentItemResponseDTO> replies = new ArrayList<>();
}
