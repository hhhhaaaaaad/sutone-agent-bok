package cn.sutone.ai.domain.content.adapter.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface ICommentRepository {

    void save(Long articleId, Long userId, Long parentId, String content);

    void logicalDelete(Long id);

    List<CommentRow> queryByArticleId(Long articleId, int page, int pageSize);

    List<CommentRow> queryByParentIds(List<Long> parentIds, int limit);

    int countByArticleId(Long articleId);

    CommentRow selectById(Long id);

    void saveLike(Long commentId, Long userId);

    void removeLike(Long commentId, Long userId);

    boolean existsLike(Long commentId, Long userId);

    int countLikes(Long commentId);

    void updateLikeCount(Long commentId, int likeCount);

    List<Map<String, Object>> countDailyByAuthor(Long userId, String since);

    record CommentRow(Long id, Long articleId, Long userId, Long parentId, String content,
                      Integer likeCount, Integer isDeleted, LocalDateTime createTime,
                      String authorName, String avatarUrl) {}
}
