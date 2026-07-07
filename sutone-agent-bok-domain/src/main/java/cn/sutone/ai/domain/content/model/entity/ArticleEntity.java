package cn.sutone.ai.domain.content.model.entity;

import cn.sutone.ai.domain.content.model.valobj.ArticleStatusVO;
import cn.sutone.ai.types.enums.ResponseCode;
import cn.sutone.ai.types.exception.AppException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleEntity {

    private Long articleId;
    private Long draftId;
    private Long authorId;
    private String title;
    private String contentMd;
    private String summary;
    private String coverUrl;
    private ArticleStatusVO status;
    private LocalDateTime publishTime;
    private ArticleMetaEntity meta;

    public static ArticleEntity publishFromDraft(Long articleId, DraftEntity draftEntity, List<String> tags) {
        draftEntity.validatePublishable();
        return ArticleEntity.builder()
                .articleId(articleId)
                .draftId(draftEntity.getDraftId())
                .authorId(draftEntity.getUserId())
                .title(draftEntity.getTitle())
                .contentMd(draftEntity.getContentMd())
                .summary(draftEntity.getSummary())
                .coverUrl(draftEntity.getCoverUrl())
                .status(ArticleStatusVO.PUBLISHED)
                .publishTime(LocalDateTime.now())
                .meta(ArticleMetaEntity.init(articleId, countWords(draftEntity.getContentMd()), tags))
                .build();
    }

    public void updateFromDraft(DraftEntity draftEntity) {
        this.title = draftEntity.getTitle();
        this.contentMd = draftEntity.getContentMd();
        this.summary = draftEntity.getSummary();
        this.coverUrl = draftEntity.getCoverUrl();
    }

    public void offline() {
        if (ArticleStatusVO.OFFLINE == this.status) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "文章已经处于下线状态");
        }
        this.status = ArticleStatusVO.OFFLINE;
    }

    public void increaseViewCount() {
        if (null != meta) {
            meta.increaseViewCount();
        }
    }

    private static int countWords(String contentMd) {
        if (null == contentMd || contentMd.isBlank()) {
            return 0;
        }
        return contentMd.trim().length();
    }

    public void updateMeta(List<String> tags) {
        if (null != meta) {
            meta.updateTags(tags);
            meta.updateWordCount(countWords(this.contentMd));
        }
    }
}
