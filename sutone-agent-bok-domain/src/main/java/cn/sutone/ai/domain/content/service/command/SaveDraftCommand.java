package cn.sutone.ai.domain.content.service.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 草稿保存命令
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveDraftCommand {

    private Long draftId;
    private String title;
    private String contentMd;
    private String summary;
    private String coverUrl;
}
