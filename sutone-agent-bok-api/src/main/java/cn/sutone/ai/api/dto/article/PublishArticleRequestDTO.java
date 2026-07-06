package cn.sutone.ai.api.dto.article;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 发布文章请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishArticleRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long draftId;
    private List<String> tags;
}
