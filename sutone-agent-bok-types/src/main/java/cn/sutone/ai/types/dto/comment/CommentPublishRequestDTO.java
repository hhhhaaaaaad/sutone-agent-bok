package cn.sutone.ai.types.dto.comment;

import lombok.Data;

@Data
public class CommentPublishRequestDTO {
    private String content;
    private Long parentId;
}
