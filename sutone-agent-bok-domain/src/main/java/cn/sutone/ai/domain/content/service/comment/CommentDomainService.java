package cn.sutone.ai.domain.content.service.comment;

import cn.sutone.ai.domain.content.adapter.repository.IArticleRepository;
import cn.sutone.ai.domain.content.adapter.repository.ICommentRepository;
import cn.sutone.ai.domain.content.adapter.repository.ICommentRepository.CommentRow;
import cn.sutone.ai.domain.content.model.entity.ArticleEntity;
import cn.sutone.ai.domain.content.service.ICommentDomainService;
import cn.sutone.ai.domain.content.service.notification.NotificationEventPublisher;
import cn.sutone.ai.types.common.NotificationConstants;
import cn.sutone.ai.types.common.RedisKeyConstants;
import cn.sutone.ai.types.dto.comment.CommentItemResponseDTO;
import cn.sutone.ai.types.dto.comment.CommentPageResponseDTO;
import cn.sutone.ai.types.exception.AppException;
import cn.sutone.ai.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CommentDomainService implements ICommentDomainService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ICommentRepository commentRepository;
    private final IArticleRepository articleRepository;
    private final RedissonClient redissonClient;
    private final NotificationEventPublisher notificationPublisher;

    public CommentDomainService(ICommentRepository commentRepository, IArticleRepository articleRepository,
                                RedissonClient redissonClient, NotificationEventPublisher notificationPublisher) {
        this.commentRepository = commentRepository;
        this.articleRepository = articleRepository;
        this.redissonClient = redissonClient;
        this.notificationPublisher = notificationPublisher;
    }

    // ==================== 发表评论 ====================

    @Override
    @Transactional
    public CommentItemResponseDTO publishComment(Long articleId, Long userId, String content, Long parentId) {
        // 内容去重
        String dupKey = RedisKeyConstants.COMMENT_DUP_PREFIX + articleId + ":" + userId;
        String lastContent = (String) redissonClient.getBucket(dupKey).get();
        if (content.equals(lastContent)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "请勿重复提交相同评论");
        }
        redissonClient.getBucket(dupKey).set(content, Duration.ofSeconds(60));

        // DB INSERT
        commentRepository.save(articleId, userId, parentId, content);

        // Redis INCR
        RAtomicLong counter = redissonClient.getAtomicLong(RedisKeyConstants.COMMENT_COUNT_PREFIX + articleId);
        counter.incrementAndGet();

        // 通知文章作者
        ArticleEntity article = articleRepository.queryArticleById(articleId);
        if (article != null && !article.getAuthorId().equals(userId)) {
            String action = "评论了你的文章";
            if (article.getTitle() != null) {
                action = "评论了你的文章《" + article.getTitle() + "》";
            }
            notificationPublisher.publish(article.getAuthorId(), "NEW_COMMENT", userId, articleId, action);
        }

        return CommentItemResponseDTO.builder()
                .articleId(articleId).authorId(userId).content(content).parentId(parentId)
                .likeCount(0).liked(false).replies(new ArrayList<>())
                .build();
    }

    // ==================== 删除评论 ====================

    @Override
    public void deleteComment(Long commentId, Long userId) {
        CommentRow comment = commentRepository.selectById(commentId);
        if (comment == null || comment.isDeleted() != null && comment.isDeleted() == 1) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "评论不存在");
        }
        // 权限：评论作者或文章作者
        ArticleEntity article = articleRepository.queryArticleById(comment.articleId());
        boolean isCommentAuthor = comment.userId().equals(userId);
        boolean isArticleAuthor = article != null && article.getAuthorId().equals(userId);
        if (!isCommentAuthor && !isArticleAuthor) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "无权删除此评论");
        }

        commentRepository.logicalDelete(commentId);

        RAtomicLong counter = redissonClient.getAtomicLong(RedisKeyConstants.COMMENT_COUNT_PREFIX + comment.articleId());
        if (counter.isExists() && counter.get() > 0) {
            counter.decrementAndGet();
        }
    }

    // ==================== 查询评论列表 ====================

    @Override
    public CommentPageResponseDTO queryComments(Long articleId, Long currentUserId, int page, int pageSize) {
        // 1. 分页查一级评论
        List<CommentRow> parents = commentRepository.queryByArticleId(articleId, page, pageSize);
        int total = commentRepository.countByArticleId(articleId);

        // 2. 收集一级评论 ID，批量查二级回复（限制 top 10）
        List<Long> parentIds = parents.stream().map(CommentRow::id).toList();
        List<CommentRow> replies = parentIds.isEmpty() ? List.of()
                : commentRepository.queryByParentIds(parentIds, 10);

        // 3. 按 parentId 分组
        Map<Long, List<CommentRow>> replyMap = replies.stream()
                .collect(Collectors.groupingBy(CommentRow::parentId));

        // 4. 批量 patch 评论点赞状态
        Set<Long> allCommentIds = new HashSet<>();
        parents.forEach(p -> allCommentIds.add(p.id()));
        replies.forEach(r -> allCommentIds.add(r.id()));
        Map<Long, Integer> likeCounts = batchGetCommentLikeCounts(allCommentIds);
        Map<Long, Boolean> likedMap = currentUserId != null
                ? batchIsCommentLiked(allCommentIds, currentUserId) : Map.of();

        // 5. 组装
        List<CommentItemResponseDTO> items = parents.stream()
                .map(p -> toDTO(p, replyMap.getOrDefault(p.id(), List.of()), likeCounts, likedMap))
                .toList();

        return CommentPageResponseDTO.builder().list(items).total(total).page(page).pageSize(pageSize).build();
    }

    private Map<Long, Integer> batchGetCommentLikeCounts(Set<Long> commentIds) {
        Map<Long, Integer> result = new HashMap<>();
        for (Long id : commentIds) {
            result.put(id, getCommentLikeCount(id));
        }
        return result;
    }

    private Map<Long, Boolean> batchIsCommentLiked(Set<Long> commentIds, Long userId) {
        Map<Long, Boolean> result = new HashMap<>();
        for (Long id : commentIds) {
            result.put(id, isLikedComment(id, userId));
        }
        return result;
    }

    private CommentItemResponseDTO toDTO(CommentRow row, List<CommentRow> replies,
                                          Map<Long, Integer> likeCounts, Map<Long, Boolean> likedMap) {
        List<CommentItemResponseDTO> replyDTOs = new ArrayList<>();
        for (CommentRow reply : replies) {
            replyDTOs.add(CommentItemResponseDTO.builder()
                    .commentId(reply.id()).articleId(reply.articleId())
                    .authorId(reply.userId()).authorName(reply.authorName()).avatarUrl(reply.avatarUrl())
                    .content(reply.content()).parentId(reply.parentId())
                    .likeCount(likeCounts.getOrDefault(reply.id(), 0))
                    .liked(likedMap.getOrDefault(reply.id(), false))
                    .createTime(reply.createTime() != null ? reply.createTime().format(DTF) : null)
                    .replies(new ArrayList<>())
                    .build());
        }
        return CommentItemResponseDTO.builder()
                .commentId(row.id()).articleId(row.articleId())
                .authorId(row.userId()).authorName(row.authorName()).avatarUrl(row.avatarUrl())
                .content(row.content()).parentId(row.parentId())
                .likeCount(likeCounts.getOrDefault(row.id(), 0))
                .liked(likedMap.getOrDefault(row.id(), false))
                .createTime(row.createTime() != null ? row.createTime().format(DTF) : null)
                .replies(replyDTOs)
                .build();
    }

    // ==================== 评论数 ====================

    @Override
    public int getCommentCount(Long articleId) {
        RAtomicLong counter = redissonClient.getAtomicLong(RedisKeyConstants.COMMENT_COUNT_PREFIX + articleId);
        if (counter.isExists()) {
            return (int) counter.get();
        }
        int dbCount = commentRepository.countByArticleId(articleId);
        counter.set(dbCount);
        return dbCount;
    }

    // ==================== 评论点赞 ====================

    @Override
    @Transactional
    public void likeComment(Long commentId, Long userId) {
        commentRepository.saveLike(commentId, userId);
        commentRepository.updateLikeCount(commentId, commentRepository.countLikes(commentId));
        try {
            RSet<Long> set = redissonClient.getSet(RedisKeyConstants.COMMENT_LIKE_PREFIX + commentId);
            set.add(userId);
            RAtomicLong counter = redissonClient.getAtomicLong(
                    RedisKeyConstants.COMMENT_LIKE_COUNT_PREFIX + commentId);
            counter.incrementAndGet();
        } catch (Exception e) {
            log.warn("Redis cache update failed, evicting comment like key comment:{}", commentId, e);
            redissonClient.getSet(RedisKeyConstants.COMMENT_LIKE_PREFIX + commentId).delete();
            redissonClient.getAtomicLong(RedisKeyConstants.COMMENT_LIKE_COUNT_PREFIX + commentId).delete();
        }
        // 通知评论作者
        CommentRow comment = commentRepository.selectById(commentId);
        if (comment != null && !comment.userId().equals(userId)) {
            notificationPublisher.publish(comment.userId(), "NEW_COMMENT_LIKE", userId, commentId, "赞了你的评论");
        }
    }

    @Override
    @Transactional
    public void unlikeComment(Long commentId, Long userId) {
        commentRepository.removeLike(commentId, userId);
        commentRepository.updateLikeCount(commentId, commentRepository.countLikes(commentId));
        try {
            RSet<Long> set = redissonClient.getSet(RedisKeyConstants.COMMENT_LIKE_PREFIX + commentId);
            set.remove(userId);
            RAtomicLong counter = redissonClient.getAtomicLong(
                    RedisKeyConstants.COMMENT_LIKE_COUNT_PREFIX + commentId);
            if (counter.isExists() && counter.get() > 0) {
                counter.decrementAndGet();
            }
        } catch (Exception e) {
            log.warn("Redis cache update failed, evicting comment like key comment:{}", commentId, e);
            redissonClient.getSet(RedisKeyConstants.COMMENT_LIKE_PREFIX + commentId).delete();
            redissonClient.getAtomicLong(RedisKeyConstants.COMMENT_LIKE_COUNT_PREFIX + commentId).delete();
        }
    }

    @Override
    public boolean isLikedComment(Long commentId, Long userId) {
        RSet<Long> set = redissonClient.getSet(RedisKeyConstants.COMMENT_LIKE_PREFIX + commentId);
        if (set.isExists()) {
            return set.contains(userId);
        }
        boolean exists = commentRepository.existsLike(commentId, userId);
        if (exists) {
            set.add(userId);
        }
        return exists;
    }

    @Override
    public int getCommentLikeCount(Long commentId) {
        RAtomicLong counter = redissonClient.getAtomicLong(
                RedisKeyConstants.COMMENT_LIKE_COUNT_PREFIX + commentId);
        if (counter.isExists()) {
            return (int) counter.get();
        }
        int dbCount = commentRepository.countLikes(commentId);
        counter.set(dbCount);
        return dbCount;
    }
}
