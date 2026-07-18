package cn.sutone.ai.domain.content.service;

import cn.sutone.ai.types.dto.comment.CommentItemResponseDTO;
import cn.sutone.ai.types.dto.comment.CommentPageResponseDTO;

public interface ICommentDomainService {

    CommentItemResponseDTO publishComment(Long articleId, Long userId, String content, Long parentId);

    void deleteComment(Long commentId, Long userId);

    CommentPageResponseDTO queryComments(Long articleId, Long currentUserId, int page, int pageSize);

    int getCommentCount(Long articleId);

    void likeComment(Long commentId, Long userId);

    void unlikeComment(Long commentId, Long userId);

    boolean isLikedComment(Long commentId, Long userId);

    int getCommentLikeCount(Long commentId);
}
