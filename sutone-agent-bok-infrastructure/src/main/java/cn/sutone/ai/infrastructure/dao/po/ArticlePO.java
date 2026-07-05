package cn.sutone.ai.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文章持久化对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticlePO {

    private Long id;
    private Long draftId;
    private Long authorId;
    private String title;
    private String contentMd;
    private String contentHtml;
    private String summary;
    private String coverUrl;
    private Integer status;
    private Integer isDeleted;
    private LocalDateTime publishTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
