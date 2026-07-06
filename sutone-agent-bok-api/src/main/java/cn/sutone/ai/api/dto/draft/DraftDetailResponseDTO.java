package cn.sutone.ai.api.dto.draft;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 草稿详情响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DraftDetailResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long draftId;
    private Long userId;
    private String title;
    private String contentMd;
    private String summary;
    private String coverUrl;
    private Integer status;
    private String statusDesc;
    private String createTime;
    private String updateTime;
}
