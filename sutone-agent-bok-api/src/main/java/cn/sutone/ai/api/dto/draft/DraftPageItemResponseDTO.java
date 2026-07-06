package cn.sutone.ai.api.dto.draft;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 草稿列表项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DraftPageItemResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long draftId;
    private String title;
    private String summary;
    private String coverUrl;
    private Integer status;
    private String statusDesc;
    private String updateTime;
}
