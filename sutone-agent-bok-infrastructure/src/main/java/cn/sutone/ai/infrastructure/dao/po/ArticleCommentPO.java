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
public class ArticleCommentPO {

    private Long id;
    private Long articleId;
    private Long userId;
    private Long parentId;
    private String content;
    private Integer likeCount;
    private Integer isDeleted;
    private LocalDateTime createTime;

    // JOIN user 额外字段
    private String authorName;
    private String avatarUrl;
}
