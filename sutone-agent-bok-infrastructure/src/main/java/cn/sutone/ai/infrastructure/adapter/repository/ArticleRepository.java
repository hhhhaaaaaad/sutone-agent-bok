package cn.sutone.ai.infrastructure.adapter.repository;

import cn.sutone.ai.domain.content.model.entity.ArticleEntity;
import cn.sutone.ai.domain.content.model.entity.ArticleMetaEntity;
import cn.sutone.ai.domain.content.model.valobj.ArticleStatusVO;
import cn.sutone.ai.domain.content.repository.IArticleRepository;
import cn.sutone.ai.infrastructure.dao.IArticleDao;
import cn.sutone.ai.infrastructure.dao.IArticleMetaDao;
import cn.sutone.ai.infrastructure.dao.po.ArticleMetaPO;
import cn.sutone.ai.infrastructure.dao.po.ArticlePO;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 文章仓储实现
 */
@Repository
public class ArticleRepository implements IArticleRepository {

    private final IArticleDao articleDao;
    private final IArticleMetaDao articleMetaDao;

    public ArticleRepository(IArticleDao articleDao, IArticleMetaDao articleMetaDao) {
        this.articleDao = articleDao;
        this.articleMetaDao = articleMetaDao;
    }

    @Override
    public Long nextArticleId() {
        return articleDao.nextArticleId();
    }

    @Override
    public Long saveArticle(ArticleEntity articleEntity) {
        articleDao.insert(toArticlePO(articleEntity));
        if (null != articleEntity.getMeta()) {
            articleMetaDao.insert(toArticleMetaPO(articleEntity.getMeta()));
        }
        return articleEntity.getArticleId();
    }

    @Override
    public ArticleEntity queryArticleById(Long articleId) {
        ArticlePO articlePO = articleDao.queryByArticleId(articleId);
        if (null == articlePO) {
            return null;
        }
        return toArticleEntity(articlePO, articleMetaDao.queryByArticleId(articleId));
    }

    @Override
    public List<ArticleEntity> queryArticlePage(Integer pageNo, Integer pageSize) {
        int offset = Math.max(pageNo - 1, 0) * pageSize;
        return articleDao.queryPage(offset, pageSize)
                .stream()
                .map(articlePO -> toArticleEntity(articlePO, articleMetaDao.queryByArticleId(articlePO.getId())))
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public Integer countArticlePage() {
        return articleDao.countPage();
    }

    @Override
    public void increaseViewCount(Long articleId) {
        articleMetaDao.increaseViewCount(articleId);
    }

    private ArticlePO toArticlePO(ArticleEntity articleEntity) {
        return ArticlePO.builder()
                .id(articleEntity.getArticleId())
                .draftId(articleEntity.getDraftId())
                .authorId(articleEntity.getAuthorId())
                .title(articleEntity.getTitle())
                .contentMd(articleEntity.getContentMd())
                .contentHtml(null)
                .summary(articleEntity.getSummary())
                .coverUrl(articleEntity.getCoverUrl())
                .status(articleEntity.getStatus().getCode())
                .publishTime(articleEntity.getPublishTime())
                .isDeleted(0)
                .build();
    }

    private ArticleMetaPO toArticleMetaPO(ArticleMetaEntity articleMetaEntity) {
        return ArticleMetaPO.builder()
                .articleId(articleMetaEntity.getArticleId())
                .wordCount(articleMetaEntity.getWordCount())
                .viewCount(articleMetaEntity.getViewCount())
                .likeCount(articleMetaEntity.getLikeCount())
                .favoriteCount(articleMetaEntity.getFavoriteCount())
                .tags(joinTags(articleMetaEntity.getTags()))
                .build();
    }

    private ArticleEntity toArticleEntity(ArticlePO articlePO, ArticleMetaPO articleMetaPO) {
        if (null == articlePO) {
            return null;
        }
        return ArticleEntity.builder()
                .articleId(articlePO.getId())
                .draftId(articlePO.getDraftId())
                .authorId(articlePO.getAuthorId())
                .title(articlePO.getTitle())
                .contentMd(articlePO.getContentMd())
                .summary(articlePO.getSummary())
                .coverUrl(articlePO.getCoverUrl())
                .status(toArticleStatusVO(articlePO.getStatus()))
                .publishTime(articlePO.getPublishTime())
                .meta(toArticleMetaEntity(articleMetaPO))
                .build();
    }

    private ArticleMetaEntity toArticleMetaEntity(ArticleMetaPO articleMetaPO) {
        if (null == articleMetaPO) {
            return null;
        }
        return ArticleMetaEntity.builder()
                .articleId(articleMetaPO.getArticleId())
                .wordCount(articleMetaPO.getWordCount())
                .viewCount(articleMetaPO.getViewCount())
                .likeCount(articleMetaPO.getLikeCount())
                .favoriteCount(articleMetaPO.getFavoriteCount())
                .tags(splitTags(articleMetaPO.getTags()))
                .build();
    }

    private ArticleStatusVO toArticleStatusVO(Integer status) {
        if (null == status) {
            return ArticleStatusVO.PUBLISHED;
        }
        for (ArticleStatusVO value : ArticleStatusVO.values()) {
            if (value.getCode().equals(status)) {
                return value;
            }
        }
        return ArticleStatusVO.PUBLISHED;
    }

    private String joinTags(List<String> tags) {
        if (null == tags || tags.isEmpty()) {
            return "";
        }
        return String.join(",", tags);
    }

    private List<String> splitTags(String tags) {
        if (null == tags || tags.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }
}
