package cn.sutone.ai.domain.content.adapter.repository;

import cn.sutone.ai.domain.content.model.entity.DraftEntity;

import java.util.List;

/**
 * 草稿仓储接口
 */
public interface IDraftRepository {

    Long nextDraftId();

    Long save(DraftEntity draftEntity);

    void update(DraftEntity draftEntity);

    DraftEntity queryById(Long draftId);

    List<DraftEntity> queryPage(Long userId, Integer pageNo, Integer pageSize);

    Integer countByUserId(Long userId);
}
