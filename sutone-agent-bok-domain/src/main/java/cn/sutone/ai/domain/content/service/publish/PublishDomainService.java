package cn.sutone.ai.domain.content.service.publish;

import cn.sutone.ai.domain.content.model.aggregate.ContentAggregate;
import org.springframework.stereotype.Service;
import cn.sutone.ai.domain.content.model.entity.ArticleEntity;
import cn.sutone.ai.domain.content.model.entity.DraftEntity;
import cn.sutone.ai.domain.content.adapter.repository.IArticleRepository;
import cn.sutone.ai.domain.content.adapter.repository.IDraftRepository;
import cn.sutone.ai.types.enums.ResponseCode;
import cn.sutone.ai.types.exception.AppException;

import java.util.List;

/**
 * 发布领域服务
 */
@Service
public class PublishDomainService {

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

        draftEntity.validateOwner(userId);

        Long articleId = articleRepository.nextArticleId();
        ContentAggregate contentAggregate = ContentAggregate.builder()
                .draftEntity(draftEntity)
                .build();

        ArticleEntity articleEntity = contentAggregate.publish(articleId, tags);
        draftRepository.update(contentAggregate.getDraftEntity());
        articleRepository.saveArticle(articleEntity);
        return articleEntity;
    }
}
