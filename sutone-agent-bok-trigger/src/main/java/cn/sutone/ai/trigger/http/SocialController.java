package cn.sutone.ai.trigger.http;

import cn.sutone.ai.api.dto.article.ArticlePageItemResponseDTO;
import cn.sutone.ai.api.response.Response;
import cn.sutone.ai.domain.content.model.entity.ArticleEntity;
import cn.sutone.ai.domain.content.service.IArticleDomainService;
import cn.sutone.ai.domain.content.service.ISocialDomainService;
import cn.sutone.ai.trigger.security.AuthUtil;
import cn.sutone.ai.types.enums.ResponseCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/v1/")
@CrossOrigin(origins = "*")
public class SocialController {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private ISocialDomainService socialService;

    @Resource
    private IArticleDomainService articleDomainService;

    /**
     * 点赞文章
     */
    @PostMapping("articles/{articleId}/like")
    public Response<Map<String, Object>> like(@PathVariable Long articleId) {
        try {
            Long userId = AuthUtil.getCurrentUserId();
            socialService.like(articleId, userId);
            int count = socialService.getLikeCount(articleId);
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(Map.of("liked", true, "likeCount", count))
                    .build();
        } catch (Exception e) {
            log.error("点赞失败 articleId:{}", articleId, e);
            return fail(e);
        }
    }

    /**
     * 取消点赞文章
     */
    @DeleteMapping("articles/{articleId}/like")
    public Response<Map<String, Object>> unlike(@PathVariable Long articleId) {
        try {
            Long userId = AuthUtil.getCurrentUserId();
            socialService.unlike(articleId, userId);
            int count = socialService.getLikeCount(articleId);
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(Map.of("liked", false, "likeCount", count))
                    .build();
        } catch (Exception e) {
            log.error("取消点赞失败 articleId:{}", articleId, e);
            return fail(e);
        }
    }

    /**
     * 查询当前用户对文章的点赞状态和点赞数
     */
    @GetMapping("articles/{articleId}/like")
    public Response<Map<String, Object>> getLikeStatus(@PathVariable Long articleId) {
        try {
            Long userId = AuthUtil.getCurrentUserId();
            boolean liked = socialService.isLiked(articleId, userId);
            int count = socialService.getLikeCount(articleId);
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(Map.of("liked", liked, "likeCount", count))
                    .build();
        } catch (Exception e) {
            log.error("查询点赞状态失败 articleId:{}", articleId, e);
            return fail(e);
        }
    }

    /**
     * 收藏文章
     */
    @PostMapping("articles/{articleId}/favorite")
    public Response<Map<String, Object>> favorite(@PathVariable Long articleId) {
        try {
            Long userId = AuthUtil.getCurrentUserId();
            socialService.favorite(articleId, userId);
            int count = socialService.getFavoriteCount(articleId);
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(Map.of("favorited", true, "favoriteCount", count))
                    .build();
        } catch (Exception e) {
            log.error("收藏失败 articleId:{}", articleId, e);
            return fail(e);
        }
    }

    /**
     * 取消收藏文章
     */
    @DeleteMapping("articles/{articleId}/favorite")
    public Response<Map<String, Object>> unfavorite(@PathVariable Long articleId) {
        try {
            Long userId = AuthUtil.getCurrentUserId();
            socialService.unfavorite(articleId, userId);
            int count = socialService.getFavoriteCount(articleId);
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(Map.of("favorited", false, "favoriteCount", count))
                    .build();
        } catch (Exception e) {
            log.error("取消收藏失败 articleId:{}", articleId, e);
            return fail(e);
        }
    }

    /**
     * 查询当前用户对文章的收藏状态和收藏数
     */
    @GetMapping("articles/{articleId}/favorite")
    public Response<Map<String, Object>> getFavoriteStatus(@PathVariable Long articleId) {
        try {
            Long userId = AuthUtil.getCurrentUserId();
            boolean favorited = socialService.isFavorited(articleId, userId);
            int count = socialService.getFavoriteCount(articleId);
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(Map.of("favorited", favorited, "favoriteCount", count))
                    .build();
        } catch (Exception e) {
            log.error("查询收藏状态失败 articleId:{}", articleId, e);
            return fail(e);
        }
    }

    /**
     * 查询当前用户收藏的文章列表（返回文章详情）
     */
    @GetMapping("user/favorites")
    public Response<List<ArticlePageItemResponseDTO>> getUserFavorites() {
        try {
            Long userId = AuthUtil.getCurrentUserId();
            Set<Long> favoriteIds = socialService.getUserFavorites(userId);
            List<ArticlePageItemResponseDTO> articles = toArticleList(favoriteIds);
            return Response.<List<ArticlePageItemResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(articles)
                    .build();
        } catch (Exception e) {
            log.error("查询用户收藏列表失败", e);
            return fail(e);
        }
    }

    /**
     * 查询当前用户点赞的文章列表（返回文章详情）
     */
    @GetMapping("user/likes")
    public Response<List<ArticlePageItemResponseDTO>> getUserLikes() {
        try {
            Long userId = AuthUtil.getCurrentUserId();
            Set<Long> likeIds = socialService.getUserLikes(userId);
            List<ArticlePageItemResponseDTO> articles = toArticleList(likeIds);
            return Response.<List<ArticlePageItemResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(articles)
                    .build();
        } catch (Exception e) {
            log.error("查询用户点赞列表失败", e);
            return fail(e);
        }
    }

    private List<ArticlePageItemResponseDTO> toArticleList(Set<Long> articleIds) {
        List<ArticlePageItemResponseDTO> result = new ArrayList<>();
        for (Long articleId : articleIds) {
            try {
                ArticleEntity article = articleDomainService.queryArticleDetailReadOnly(articleId);
                result.add(toPageItemDTO(article));
            } catch (Exception ignored) {
                // 文章可能已被删除，跳过
            }
        }
        return result;
    }

    private ArticlePageItemResponseDTO toPageItemDTO(ArticleEntity article) {
        ArticlePageItemResponseDTO.ArticlePageItemResponseDTOBuilder builder = ArticlePageItemResponseDTO.builder()
                .articleId(article.getArticleId())
                .authorId(article.getAuthorId())
                .authorName(article.getAuthorName())
                .avatarUrl(article.getAvatarUrl())
                .title(article.getTitle())
                .summary(article.getSummary())
                .coverUrl(article.getCoverUrl())
                .publishTime(article.getPublishTime() != null ? article.getPublishTime().format(DTF) : null);

        if (article.getMeta() != null) {
            builder.viewCount(article.getMeta().getViewCount())
                    .likeCount(article.getMeta().getLikeCount())
                    .favoriteCount(article.getMeta().getFavoriteCount())
                    .tags(article.getMeta().getTags());
        }
        return builder.build();
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
