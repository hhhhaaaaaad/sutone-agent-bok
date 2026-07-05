package cn.sutone.ai.domain.content.model.aggregate;

import cn.sutone.ai.domain.content.model.entity.ArticleEntity;
import cn.sutone.ai.domain.content.model.entity.DraftEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 内容发布聚合
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentAggregate {

    private DraftEntity draftEntity;
    private ArticleEntity articleEntity;

    public ArticleEntity publish(Long articleId, List<String> tags) {
        this.draftEntity.markPublished();
        this.articleEntity = ArticleEntity.publishFromDraft(articleId, draftEntity, tags);
        return this.articleEntity;
    }
}
