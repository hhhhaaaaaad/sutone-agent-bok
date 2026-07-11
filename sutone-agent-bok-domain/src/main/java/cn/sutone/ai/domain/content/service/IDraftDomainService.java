package cn.sutone.ai.domain.content.service;

import cn.sutone.ai.domain.content.model.entity.DraftEntity;
import cn.sutone.ai.domain.content.service.command.SaveDraftCommand;

import java.util.List;

/**
 * 草稿领域服务接口
 */
public interface IDraftDomainService {

    DraftEntity saveDraft(Long userId, SaveDraftCommand command);

    DraftEntity queryDraftDetail(Long draftId, Long userId);

    List<DraftEntity> queryDraftPage(Long userId, Integer pageNo, Integer pageSize);

    DraftEntity discardDraft(Long draftId, Long userId);

    Integer countByUserId(Long userId);

}
