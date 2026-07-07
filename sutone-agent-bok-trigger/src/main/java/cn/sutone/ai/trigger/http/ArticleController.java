package cn.sutone.ai.trigger.http;

import cn.sutone.ai.api.IArticleService;
import cn.sutone.ai.api.dto.PageResponseDTO;
import cn.sutone.ai.api.dto.article.*;
import cn.sutone.ai.api.response.Response;
import cn.sutone.ai.domain.content.model.entity.ArticleEntity;
import cn.sutone.ai.domain.content.service.IArticleDomainService;
import cn.sutone.ai.domain.content.service.IPublishDomainService;
import cn.sutone.ai.types.enums.ResponseCode;
import cn.sutone.ai.types.exception.AppException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * 文章 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/")
@CrossOrigin(origins = "*")
public class ArticleController implements IArticleService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Long DEFAULT_USER_ID = 1L;

    @Resource
    private IPublishDomainService publishDomainService;

    @Resource
    private IArticleDomainService articleDomainService;

    @PostMapping("articles/publish")
    @Override
    public Response<PublishArticleResponseDTO> publishArticle(@RequestBody PublishArticleRequestDTO requestDTO) {
        try {
            List<String> tags = requestDTO.getTags() != null ? requestDTO.getTags() : Collections.emptyList();
            ArticleEntity article = publishDomainService.publish(DEFAULT_USER_ID, requestDTO.getDraftId(), tags);

            return Response.<PublishArticleResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(PublishArticleResponseDTO.builder()
                            .articleId(article.getArticleId())
                            .draftId(article.getDraftId())
                            .articleUrl("/articles/" + article.getArticleId())
                            .publishTime(article.getPublishTime() != null ? article.getPublishTime().format(DTF) : null)
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("发布文章失败 draftId:{}", requestDTO.getDraftId(), e);
            return fail(e);
        }
    }

    @GetMapping("articles/page")
    @Override
    public Response<PageResponseDTO<ArticlePageItemResponseDTO>> queryArticlePage(
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            List<ArticleEntity> articles = articleDomainService.queryArticlePage(pageNo, pageSize);
            List<ArticlePageItemResponseDTO> items = articles.stream()
                    .map(this::toPageItemDTO)
                    .toList();

            return Response.<PageResponseDTO<ArticlePageItemResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(PageResponseDTO.<ArticlePageItemResponseDTO>builder()
                            .total(items.size())
                            .pageNo(pageNo)
                            .pageSize(pageSize)
                            .list(items)
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("查询文章列表失败", e);
            return fail(e);
        }
    }

    @GetMapping("articles/{articleId}")
    @Override
    public Response<ArticleDetailResponseDTO> queryArticleDetail(@PathVariable Long articleId) {
        try {
            ArticleEntity article = articleDomainService.queryArticleDetail(articleId);
            return Response.<ArticleDetailResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(toDetailDTO(article))
                    .build();
        } catch (Exception e) {
            log.error("查询文章详情失败 articleId:{}", articleId, e);
            return fail(e);
        }
    }

    private ArticleDetailResponseDTO toDetailDTO(ArticleEntity article) {
        ArticleDetailResponseDTO.ArticleDetailResponseDTOBuilder builder = ArticleDetailResponseDTO.builder()
                .articleId(article.getArticleId())
                .authorId(article.getAuthorId())
                .title(article.getTitle())
                .summary(article.getSummary())
                .contentMd(article.getContentMd())
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

    private ArticlePageItemResponseDTO toPageItemDTO(ArticleEntity article) {
        ArticlePageItemResponseDTO.ArticlePageItemResponseDTOBuilder builder = ArticlePageItemResponseDTO.builder()
                .articleId(article.getArticleId())
                .authorId(article.getAuthorId())
                .title(article.getTitle())
                .summary(article.getSummary())
                .coverUrl(article.getCoverUrl())
                .publishTime(article.getPublishTime() != null ? article.getPublishTime().format(DTF) : null);

        if (article.getMeta() != null) {
            builder.viewCount(article.getMeta().getViewCount())
                    .tags(article.getMeta().getTags());
        }
        return builder.build();
    }

    private <T> Response<T> fail(Exception e) {
        String code = ResponseCode.UN_ERROR.getCode();
        String info = e.getMessage();
        if (e instanceof AppException ae) {
            code = ae.getCode() != null ? ae.getCode() : code;
            info = ae.getInfo() != null ? ae.getInfo() : info;
        }
        return Response.<T>builder().code(code).info(info).build();
    }
}
