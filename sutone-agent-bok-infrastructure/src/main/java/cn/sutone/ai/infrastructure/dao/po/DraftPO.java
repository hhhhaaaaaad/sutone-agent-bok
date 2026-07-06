package cn.sutone.ai.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 草稿持久化对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DraftPO {

    private Long id;
    private Long userId;
    private String title;
    private String contentMd;
    private String summary;
    private String coverUrl;
    private Integer status;
    private Integer isDeleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
