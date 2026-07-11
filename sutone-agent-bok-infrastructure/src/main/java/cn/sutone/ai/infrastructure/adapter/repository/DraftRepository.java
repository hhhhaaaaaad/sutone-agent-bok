package cn.sutone.ai.infrastructure.adapter.repository;

import cn.sutone.ai.domain.content.model.entity.DraftEntity;
import cn.sutone.ai.domain.content.model.valobj.DraftStatusVO;
import cn.sutone.ai.domain.content.adapter.repository.IDraftRepository;
import cn.sutone.ai.infrastructure.dao.IDraftDao;
import cn.sutone.ai.infrastructure.dao.po.DraftPO;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

/**
 * 草稿仓储实现
 */
@Repository
public class DraftRepository implements IDraftRepository {

    private final IDraftDao draftDao;

    public DraftRepository(IDraftDao draftDao) {
        this.draftDao = draftDao;
    }

    @Override
    public Long nextDraftId() {
        return draftDao.nextDraftId();
    }

    @Override
    public Long save(DraftEntity draftEntity) {
        draftDao.insert(toDraftPO(draftEntity));
        return draftEntity.getDraftId();
    }

    @Override
    public void update(DraftEntity draftEntity) {
        draftDao.update(toDraftPO(draftEntity));
    }

    @Override
    public DraftEntity queryById(Long draftId) {
        return toDraftEntity(draftDao.queryById(draftId));
    }

    @Override
    public List<DraftEntity> queryPage(Long userId, Integer pageNo, Integer pageSize) {
        int offset = Math.max(pageNo - 1, 0) * pageSize;
        return draftDao.queryPage(userId, offset, pageSize)
                .stream()
                .map(this::toDraftEntity)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public Integer countByUserId(Long userId) {
        return draftDao.countByUserId(userId);
    }

    private DraftPO toDraftPO(DraftEntity draftEntity) {
        return DraftPO.builder()
                .id(draftEntity.getDraftId())
                .userId(draftEntity.getUserId())
                .title(draftEntity.getTitle())
                .contentMd(draftEntity.getContentMd())
                .summary(draftEntity.getSummary())
                .coverUrl(draftEntity.getCoverUrl())
                .status(draftEntity.getStatus().getCode())
                .isDeleted(0)
                .createTime(draftEntity.getCreateTime())
                .updateTime(draftEntity.getUpdateTime())
                .build();
    }

    private DraftEntity toDraftEntity(DraftPO draftPO) {
        if (null == draftPO) {
            return null;
        }
        return DraftEntity.builder()
                .draftId(draftPO.getId())
                .userId(draftPO.getUserId())
                .title(draftPO.getTitle())
                .contentMd(draftPO.getContentMd())
                .summary(draftPO.getSummary())
                .coverUrl(draftPO.getCoverUrl())
                .status(toDraftStatusVO(draftPO.getStatus()))
                .createTime(draftPO.getCreateTime())
                .updateTime(draftPO.getUpdateTime())
                .build();
    }

    private DraftStatusVO toDraftStatusVO(Integer status) {
        if (null == status) {
            return DraftStatusVO.EDITING;
        }
        for (DraftStatusVO value : DraftStatusVO.values()) {
            if (value.getCode().equals(status)) {
                return value;
            }
        }
        return DraftStatusVO.EDITING;
    }
}
