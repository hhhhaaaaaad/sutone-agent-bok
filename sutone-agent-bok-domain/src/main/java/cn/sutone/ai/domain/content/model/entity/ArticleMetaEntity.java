package cn.sutone.ai.domain.content.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 文章元数据实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleMetaEntity {

    private Long articleId;
    private Integer wordCount;
    private Integer viewCount;
    private Integer likeCount;
    private Integer favoriteCount;
    private Integer commentCount;
    private List<String> tags;

    public static ArticleMetaEntity init(Long articleId, Integer wordCount, List<String> tags) {
        return ArticleMetaEntity.builder()
                .articleId(articleId)
                .wordCount(null == wordCount ? 0 : wordCount)
                .viewCount(0)
                .likeCount(0)
                .favoriteCount(0)
                .tags(null == tags ? new ArrayList<>() : new ArrayList<>(tags))
                .build();
    }

    public void increaseViewCount() {
        this.viewCount = null == this.viewCount ? 1 : this.viewCount + 1;
    }

    public void updateTags(List<String> tags) {
        this.tags = new ArrayList<>(tags);
    }

    public void updateWordCount(int wordCount) {
        this.wordCount = wordCount;
    }
}
