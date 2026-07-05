package cn.sutone.ai.domain.content.model.entity;

import cn.sutone.ai.domain.content.model.valobj.DraftStatusVO;
import cn.sutone.ai.types.enums.ResponseCode;
import cn.sutone.ai.types.exception.AppException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 草稿实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DraftEntity {

    private Long draftId;
    private Long userId;
    private String title;
    private String contentMd;
    private String summary;
    private String coverUrl;
    private DraftStatusVO status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static DraftEntity initNewDraft(Long draftId, Long userId, String title, String contentMd, String summary, String coverUrl) {
        LocalDateTime now = LocalDateTime.now();
        return DraftEntity.builder()
                .draftId(draftId)
                .userId(userId)
                .title(title)
                .contentMd(contentMd)
                .summary(summary)
                .coverUrl(coverUrl)
                .status(DraftStatusVO.EDITING)
                .createTime(now)
                .updateTime(now)
                .build();
    }

    public void updateContent(String title, String contentMd, String summary, String coverUrl) {
        checkEditable();
        this.title = title;
        this.contentMd = contentMd;
        this.summary = summary;
        this.coverUrl = coverUrl;
        this.updateTime = LocalDateTime.now();
    }

    public void discard() {
        checkEditable();
        this.status = DraftStatusVO.DISCARDED;
        this.updateTime = LocalDateTime.now();
    }

    public void markPublished() {
        checkEditable();
        validatePublishable();
        this.status = DraftStatusVO.PUBLISHED;
        this.updateTime = LocalDateTime.now();
    }

    public void checkEditable() {
        if (DraftStatusVO.EDITING != this.status) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "当前草稿状态不允许继续编辑");
        }
    }

    public void validatePublishable() {
        if (null == title || title.isBlank()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "发布前标题不能为空");
        }
        if (null == contentMd || contentMd.isBlank()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "发布前正文不能为空");
        }
    }

    public void validateOwner(Long userId) {
        if (null == userId || !userId.equals(this.userId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "无权操作该草稿");
        }
    }
}
