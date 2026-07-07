package cn.sutone.ai.domain.content.service.draft;

import cn.sutone.ai.domain.content.model.entity.DraftEntity;
import cn.sutone.ai.domain.content.service.IDraftDomainService;
import org.springframework.stereotype.Service;
import cn.sutone.ai.domain.content.adapter.repository.IDraftRepository;
import cn.sutone.ai.domain.content.service.command.SaveDraftCommand;
import cn.sutone.ai.types.enums.ResponseCode;
import cn.sutone.ai.types.exception.AppException;

import java.util.List;

/**
 * 草稿领域服务
 */
@Service
public class DraftDomainService implements IDraftDomainService {

    private final IDraftRepository draftRepository;

    public DraftDomainService(IDraftRepository draftRepository) {
        this.draftRepository = draftRepository;
    }

    public DraftEntity saveDraft(Long userId, SaveDraftCommand command) {
        if (null == command.getDraftId()) {
            Long draftId = draftRepository.nextDraftId();
            DraftEntity draftEntity = DraftEntity.initNewDraft(
                    draftId,
                    userId,
                    command.getTitle(),
                    command.getContentMd(),
                    command.getSummary(),
                    command.getCoverUrl()
            );
            draftRepository.save(draftEntity);
            return draftEntity;
        }

        DraftEntity draftEntity = getOwnedDraft(command.getDraftId(), userId);
        draftEntity.updateContent(
                command.getTitle(),
                command.getContentMd(),
                command.getSummary(),
                command.getCoverUrl()
        );
        draftRepository.update(draftEntity);
        return draftEntity;
    }

    public DraftEntity queryDraftDetail(Long draftId, Long userId) {
        return getOwnedDraft(draftId, userId);
    }

    public List<DraftEntity> queryDraftPage(Long userId, Integer pageNo, Integer pageSize) {
        return draftRepository.queryPage(userId, pageNo, pageSize);
    }

    public DraftEntity discardDraft(Long draftId, Long userId) {
        DraftEntity draftEntity = getOwnedDraft(draftId, userId);
        draftEntity.discard();
        draftRepository.update(draftEntity);
        return draftEntity;
    }

    private DraftEntity getOwnedDraft(Long draftId, Long userId) {
        DraftEntity draftEntity = draftRepository.queryById(draftId);
        if (null == draftEntity) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "草稿不存在");
        }
        draftEntity.validateOwner(userId);
        return draftEntity;
    }
}
