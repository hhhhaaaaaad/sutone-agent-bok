package cn.sutone.ai.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文章收藏持久化对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleFavoritePO {

    private Long id;
    private Long articleId;
    private Long userId;
    private LocalDateTime createTime;
}
