package cn.sutone.ai.api.dto.article;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 文章列表项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticlePageItemResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long articleId;
    private Long authorId;
    private String authorName;
    private String avatarUrl;
    private String title;
    private String summary;
    private String coverUrl;
    private String publishTime;
    private Integer viewCount;
    private Integer likeCount;
    private Integer favoriteCount;
    private List<String> tags;
}
