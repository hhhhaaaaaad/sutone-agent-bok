package cn.sutone.ai.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleViewDailyPO {

    private Long id;
    private Long articleId;
    private LocalDate date;
    private Integer viewCount;
    private Integer likeCount;
    private Integer favoriteCount;
}
