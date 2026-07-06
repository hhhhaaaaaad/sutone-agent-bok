package cn.sutone.ai.api.dto.article;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 发布文章响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishArticleResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long articleId;
    private Long draftId;
    private String articleUrl;
    private String publishTime;
}
