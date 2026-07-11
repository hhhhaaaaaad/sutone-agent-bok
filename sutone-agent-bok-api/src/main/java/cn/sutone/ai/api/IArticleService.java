package cn.sutone.ai.api;

import cn.sutone.ai.api.dto.PageResponseDTO;
import cn.sutone.ai.api.dto.article.*;
import cn.sutone.ai.api.response.Response;

/**
 * 文章服务接口
 */
public interface IArticleService {

    Response<PublishArticleResponseDTO> publishArticle(PublishArticleRequestDTO requestDTO);

    Response<PageResponseDTO<ArticlePageItemResponseDTO>> queryArticlePage(Integer pageNo, Integer pageSize, Long userId, String keyword);

    Response<ArticleDetailResponseDTO> queryArticleDetail(Long articleId);

    Response<RevertToDraftResponseDTO> revertToDraft(Long articleId);
}
