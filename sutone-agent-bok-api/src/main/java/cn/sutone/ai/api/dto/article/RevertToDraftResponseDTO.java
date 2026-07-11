package cn.sutone.ai.api.dto.article;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 回退草稿编辑响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevertToDraftResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long draftId;
    private Integer status;
    private String statusDesc;
}
