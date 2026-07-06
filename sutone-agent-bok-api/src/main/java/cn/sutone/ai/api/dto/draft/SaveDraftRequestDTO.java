package cn.sutone.ai.api.dto.draft;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 保存/更新草稿请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveDraftRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 草稿ID，为空表示新建，非空表示更新 */
    private Long draftId;
    private String title;
    private String contentMd;
    private String summary;
    private String coverUrl;
}
