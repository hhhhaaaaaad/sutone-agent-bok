package cn.sutone.ai.infrastructure.adapter.repository;

import cn.sutone.ai.domain.content.adapter.repository.ICommentRepository;
import cn.sutone.ai.infrastructure.dao.IArticleCommentDao;
import cn.sutone.ai.infrastructure.dao.ICommentLikeDao;
import cn.sutone.ai.infrastructure.dao.po.ArticleCommentPO;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class CommentRepository implements ICommentRepository {

    private final IArticleCommentDao commentDao;
    private final ICommentLikeDao commentLikeDao;

    public CommentRepository(IArticleCommentDao commentDao, ICommentLikeDao commentLikeDao) {
        this.commentDao = commentDao;
        this.commentLikeDao = commentLikeDao;
    }

    @Override
    public void save(Long articleId, Long userId, Long parentId, String content) {
        commentDao.insert(ArticleCommentPO.builder()
                .articleId(articleId).userId(userId).parentId(parentId).content(content).build());
    }

    @Override
    public void logicalDelete(Long id) {
        commentDao.logicalDelete(id);
    }

    @Override
    public List<CommentRow> queryByArticleId(Long articleId, int page, int pageSize) {
        int offset = Math.max(page - 1, 0) * pageSize;
        return commentDao.queryByArticleId(articleId, offset, pageSize).stream()
                .map(this::toRow).toList();
    }

    @Override
    public List<CommentRow> queryByParentIds(List<Long> parentIds, int limit) {
        if (parentIds == null || parentIds.isEmpty()) {
            return List.of();
        }
        return commentDao.queryByParentIds(parentIds, limit).stream()
                .map(this::toRow).toList();
    }

    @Override
    public int countByArticleId(Long articleId) {
        return commentDao.countByArticleId(articleId);
    }

    @Override
    public CommentRow selectById(Long id) {
        ArticleCommentPO po = commentDao.selectById(id);
        return po != null ? toRow(po) : null;
    }

    @Override
    public void saveLike(Long commentId, Long userId) {
        commentLikeDao.insert(commentId, userId);
    }

    @Override
    public void removeLike(Long commentId, Long userId) {
        commentLikeDao.delete(commentId, userId);
    }

    @Override
    public boolean existsLike(Long commentId, Long userId) {
        return commentLikeDao.exists(commentId, userId);
    }

    @Override
    public int countLikes(Long commentId) {
        return commentLikeDao.countByCommentId(commentId);
    }

    @Override
    public void updateLikeCount(Long commentId, int likeCount) {
        commentDao.updateLikeCount(commentId, likeCount);
    }

    @Override
    public List<Map<String, Object>> countDailyByAuthor(Long userId, String since) {
        return commentDao.countDailyByAuthor(userId, since);
    }

    private CommentRow toRow(ArticleCommentPO po) {
        return new CommentRow(po.getId(), po.getArticleId(), po.getUserId(), po.getParentId(),
                po.getContent(), po.getLikeCount(), po.getIsDeleted(), po.getCreateTime(),
                po.getAuthorName(), po.getAvatarUrl());
    }
}
