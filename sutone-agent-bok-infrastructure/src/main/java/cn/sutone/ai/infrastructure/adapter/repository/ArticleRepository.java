package cn.sutone.ai.infrastructure.adapter.repository;

import cn.sutone.ai.domain.content.model.entity.ArticleEntity;
import cn.sutone.ai.domain.content.model.entity.ArticleMetaEntity;
import cn.sutone.ai.domain.content.model.valobj.ArticleStatusVO;
import cn.sutone.ai.domain.content.adapter.repository.IArticleRepository;
import cn.sutone.ai.infrastructure.dao.IArticleDao;
import cn.sutone.ai.infrastructure.dao.IArticleMetaDao;
import cn.sutone.ai.infrastructure.dao.po.ArticleMetaPO;
import cn.sutone.ai.infrastructure.dao.po.ArticlePO;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
    public Long updateArticle(ArticleEntity articleEntity) {
        articleDao.update(toArticlePO(articleEntity));
        if (null != articleEntity.getMeta()) {
            articleMetaDao.updateByArticleId(toArticleMetaPO(articleEntity.getMeta()));
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
    public ArticleEntity queryArticleByDraftId(Long draftId) {
        ArticlePO articlePO = articleDao.queryByDraftId(draftId);
        if (null == articlePO) {
            return null;
        }
        return toArticleEntity(articlePO, articleMetaDao.queryByArticleId(articlePO.getId()));
    }

    @Override
    public List<ArticleEntity> queryArticlePage(Integer pageNo, Integer pageSize, Long userId, String keyword) {
        int offset = Math.max(pageNo - 1, 0) * pageSize;
        String kw = null == keyword || keyword.isBlank() ? null : keyword.trim();
        return articleDao.queryPage(offset, pageSize, userId, kw)
                .stream()
                .map(articlePO -> toArticleEntity(articlePO, articleMetaDao.queryByArticleId(articlePO.getId())))
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public Integer countArticlePage(Long userId, String keyword) {
        String kw = null == keyword || keyword.isBlank() ? null : keyword.trim();
        return articleDao.countPage(userId, kw);
    }

    @Override
    public List<ArticleEntity> queryArticlePageCursor(Long cursor, Integer pageSize, Long userId, String keyword) {
        String kw = null == keyword || keyword.isBlank() ? null : keyword.trim();
        return articleDao.queryPageCursor(cursor, pageSize, userId, kw)
                .stream()
                .map(articlePO -> toArticleEntity(articlePO, articleMetaDao.queryByArticleId(articlePO.getId())))
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public void increaseViewCount(Long articleId) {
        articleMetaDao.increaseViewCount(articleId);
    }

    @Override
    public int queryLikeCount(Long articleId) {
        Integer count = articleMetaDao.selectLikeCount(articleId);
        return count != null ? count : 0;
    }

    @Override
    public int queryFavoriteCount(Long articleId) {
        Integer count = articleMetaDao.selectFavoriteCount(articleId);
        return count != null ? count : 0;
    }

    @Override
    public void increaseLikeCount(Long articleId) {
        articleMetaDao.increaseLikeCount(articleId);
    }

    @Override
    public void decreaseLikeCount(Long articleId) {
        articleMetaDao.decreaseLikeCount(articleId);
    }

    @Override
    public void increaseFavoriteCount(Long articleId) {
        articleMetaDao.increaseFavoriteCount(articleId);
    }

    @Override
    public void decreaseFavoriteCount(Long articleId) {
        articleMetaDao.decreaseFavoriteCount(articleId);
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
                .authorName(articlePO.getAuthorName())
                .avatarUrl(articlePO.getAvatarUrl())
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
        return tags.stream()
                .flatMap(tag -> Arrays.stream(tag.split("[，,、\\s]+"))) // 兼容中文逗号/英文逗号/顿号/空格
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.joining(","));
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
