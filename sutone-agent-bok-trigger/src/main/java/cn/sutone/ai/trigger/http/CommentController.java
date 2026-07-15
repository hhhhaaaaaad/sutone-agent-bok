package cn.sutone.ai.trigger.http;

import cn.sutone.ai.api.response.Response;
import cn.sutone.ai.domain.content.service.ICommentDomainService;
import cn.sutone.ai.trigger.security.AuthUtil;
import cn.sutone.ai.types.dto.comment.CommentItemResponseDTO;
import cn.sutone.ai.types.dto.comment.CommentPageResponseDTO;
import cn.sutone.ai.types.dto.comment.CommentPublishRequestDTO;
import cn.sutone.ai.types.enums.ResponseCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/")
@CrossOrigin(origins = "*")
public class CommentController {

    @Resource
    private ICommentDomainService commentService;

    /** 发表评论 */
    @PostMapping("articles/{articleId}/comments")
    public Response<CommentItemResponseDTO> publish(@PathVariable Long articleId,
                                                     @RequestBody CommentPublishRequestDTO req) {
        try {
            Long userId = AuthUtil.getCurrentUserId();
            CommentItemResponseDTO result = commentService.publishComment(
                    articleId, userId, req.getContent(), req.getParentId());
            return Response.<CommentItemResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result)
                    .build();
        } catch (Exception e) {
            log.error("发表评论失败 articleId:{}", articleId, e);
            return fail(e);
        }
    }

    /** 删除评论 */
    @DeleteMapping("articles/{articleId}/comments/{commentId}")
    public Response<Void> delete(@PathVariable Long articleId, @PathVariable Long commentId) {
        try {
            Long userId = AuthUtil.getCurrentUserId();
            commentService.deleteComment(commentId, userId);
            return Response.<Void>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("删除评论失败 commentId:{}", commentId, e);
            return fail(e);
        }
    }

    /** 分页查询评论列表 */
    @GetMapping("articles/{articleId}/comments")
    public Response<CommentPageResponseDTO> list(@PathVariable Long articleId,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int pageSize) {
        try {
            Long currentUserId = AuthUtil.getCurrentUserIdOrNull();
            CommentPageResponseDTO result = commentService.queryComments(articleId, currentUserId, page, pageSize);
            return Response.<CommentPageResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result)
                    .build();
        } catch (Exception e) {
            log.error("查询评论列表失败 articleId:{}", articleId, e);
            return fail(e);
        }
    }

    /** 点赞评论 */
    @PostMapping("comments/{commentId}/like")
    public Response<Map<String, Object>> like(@PathVariable Long commentId) {
        try {
            Long userId = AuthUtil.getCurrentUserId();
            commentService.likeComment(commentId, userId);
            int count = commentService.getCommentLikeCount(commentId);
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(Map.of("liked", true, "likeCount", count))
                    .build();
        } catch (Exception e) {
            log.error("评论点赞失败 commentId:{}", commentId, e);
            return fail(e);
        }
    }

    /** 取消点赞评论 */
    @DeleteMapping("comments/{commentId}/like")
    public Response<Map<String, Object>> unlike(@PathVariable Long commentId) {
        try {
            Long userId = AuthUtil.getCurrentUserId();
            commentService.unlikeComment(commentId, userId);
            int count = commentService.getCommentLikeCount(commentId);
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(Map.of("liked", false, "likeCount", count))
                    .build();
        } catch (Exception e) {
            log.error("取消评论点赞失败 commentId:{}", commentId, e);
            return fail(e);
        }
    }

    private <T> Response<T> fail(Exception e) {
        String code = ResponseCode.UN_ERROR.getCode();
        String info = e.getMessage();
        if (e instanceof cn.sutone.ai.types.exception.AppException ae) {
            code = ae.getCode() != null ? ae.getCode() : code;
            info = ae.getInfo() != null ? ae.getInfo() : info;
        }
        return Response.<T>builder().code(code).info(info).build();
    }
}
