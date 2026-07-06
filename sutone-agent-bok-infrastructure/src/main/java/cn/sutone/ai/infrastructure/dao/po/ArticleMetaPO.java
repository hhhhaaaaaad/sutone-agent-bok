package cn.sutone.ai.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文章元数据持久化对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleMetaPO {

    private Long id;
    private Long articleId;
    private Integer wordCount;
    private Integer viewCount;
    private Integer likeCount;
    private Integer favoriteCount;
    private String tags;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
