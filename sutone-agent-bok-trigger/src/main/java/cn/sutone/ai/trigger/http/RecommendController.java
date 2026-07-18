package cn.sutone.ai.trigger.http;

import cn.sutone.ai.api.dto.article.ArticlePageItemResponseDTO;
import cn.sutone.ai.api.response.Response;
import cn.sutone.ai.domain.content.model.entity.ArticleEntity;
import cn.sutone.ai.domain.content.service.ISocialDomainService;
import cn.sutone.ai.domain.content.service.ICommentDomainService;
import cn.sutone.ai.domain.content.service.recommend.RecommendService;
import cn.sutone.ai.trigger.security.AuthUtil;
import cn.sutone.ai.types.enums.ResponseCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/")
@CrossOrigin(origins = "*")
public class RecommendController {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private RecommendService recommendService;

    @Resource
    private ISocialDomainService socialService;

    @Resource
    private ICommentDomainService commentService;

    @GetMapping("articles/recommend")
    public Response<List<ArticlePageItemResponseDTO>> recommend(@RequestParam(defaultValue = "10") int n) {
        try {
            Long userId = AuthUtil.getCurrentUserId();
            List<ArticleEntity> articles = recommendService.recommend(userId, n);
            List<ArticlePageItemResponseDTO> items = new ArrayList<>();
            for (ArticleEntity a : articles) {
                items.add(toDTO(a));
            }
            return Response.<List<ArticlePageItemResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(items)
                    .build();
        } catch (Exception e) {
            log.error("推荐查询失败", e);
            return fail(e);
        }
    }

    private ArticlePageItemResponseDTO toDTO(ArticleEntity article) {
        Long articleId = article.getArticleId();
        return ArticlePageItemResponseDTO.builder()
                .articleId(articleId)
                .authorId(article.getAuthorId())
                .authorName(article.getAuthorName())
                .avatarUrl(article.getAvatarUrl())
                .title(article.getTitle())
                .summary(article.getSummary())
                .coverUrl(article.getCoverUrl())
                .publishTime(article.getPublishTime() != null ? article.getPublishTime().format(DTF) : null)
                .viewCount(article.getMeta() != null ? article.getMeta().getViewCount() : 0)
                .likeCount(socialService.getLikeCount(articleId))
                .favoriteCount(socialService.getFavoriteCount(articleId))
                .commentCount(commentService.getCommentCount(articleId))
                .tags(article.getMeta() != null ? article.getMeta().getTags() : null)
                .build();
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
