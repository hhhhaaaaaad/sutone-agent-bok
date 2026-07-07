package cn.sutone.ai.domain.content.service.publish;

import cn.sutone.ai.domain.content.model.aggregate.ContentAggregate;
import cn.sutone.ai.domain.content.service.IPublishDomainService;
import org.springframework.stereotype.Service;
import cn.sutone.ai.domain.content.model.entity.ArticleEntity;
import cn.sutone.ai.domain.content.model.entity.DraftEntity;
import cn.sutone.ai.domain.content.adapter.repository.IArticleRepository;
import cn.sutone.ai.domain.content.adapter.repository.IDraftRepository;
import cn.sutone.ai.domain.content.model.valobj.ArticleStatusVO;
import cn.sutone.ai.types.enums.ResponseCode;
import cn.sutone.ai.types.exception.AppException;

import java.util.List;

/**
 * 发布领域服务
 */
@Service
public class PublishDomainService implements IPublishDomainService {

    private final IDraftRepository draftRepository;
    private final IArticleRepository articleRepository;

    public PublishDomainService(IDraftRepository draftRepository, IArticleRepository articleRepository) {
        this.draftRepository = draftRepository;
        this.articleRepository = articleRepository;
    }

    public ArticleEntity publish(Long userId, Long draftId, List<String> tags) {
        DraftEntity draftEntity = draftRepository.queryById(draftId);
        if (null == draftEntity) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "草稿不存在");
        }

        // 验证当前的用户是否为该草稿的主人
        draftEntity.validateOwner(userId);

        // 检查该草稿是否已关联文章（revert-to-draft 后的重新发布场景）
        ArticleEntity existingArticle = articleRepository.queryArticleByDraftId(draftId);
        if (existingArticle != null) {
            return rePublishFromExisting(draftEntity, existingArticle, tags);
        }

        // 常规首次发布
        // todo 后续可以改成redis生成id再尝试保存，失败则回滚redis中的值
        Long articleId = articleRepository.nextArticleId();
        ContentAggregate contentAggregate = ContentAggregate.builder()
                .draftEntity(draftEntity)
                .build();

        ArticleEntity articleEntity = contentAggregate.publish(articleId, tags);
        draftRepository.update(contentAggregate.getDraftEntity());
        articleRepository.saveArticle(articleEntity);
        return articleEntity;
    }

    private ArticleEntity rePublishFromExisting(DraftEntity draftEntity, ArticleEntity articleEntity, List<String> tags) {
        // 1. 草稿状态改为 已发布
        draftEntity.markPublished();
        // 2. 将文章内容改为草稿的
        articleEntity.updateFromDraft(draftEntity);
        articleEntity.updateMeta(tags);
        // 3. 更新草稿和文章到数据库
        draftRepository.update(draftEntity);
        articleRepository.updateArticle(articleEntity);
        return articleEntity;
    }

    @Override
    public DraftEntity revertToDraft(Long userId, Long articleId) {
        ArticleEntity articleEntity = articleRepository.queryArticleById(articleId);
        if (null == articleEntity) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "文章不存在");
        }
        if (ArticleStatusVO.PUBLISHED != articleEntity.getStatus()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "仅已发布文章可以回退编辑");
        }

        DraftEntity draftEntity = draftRepository.queryById(articleEntity.getDraftId());
        if (null == draftEntity) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "关联草稿不存在");
        }
        draftEntity.validateOwner(userId);

        // 将原草稿重置为可编辑状态（让文章绑定的草稿设置为可编辑）
        draftEntity.revertToEditable();
        draftRepository.update(draftEntity);

        return draftEntity;
    }
}
