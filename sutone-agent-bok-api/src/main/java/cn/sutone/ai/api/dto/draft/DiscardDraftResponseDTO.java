package cn.sutone.ai.api.dto.draft;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 废弃草稿响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscardDraftResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long draftId;
    private Integer status;
    private String statusDesc;
}
