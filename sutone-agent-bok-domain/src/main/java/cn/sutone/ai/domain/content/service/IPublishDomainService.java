package cn.sutone.ai.domain.content.service;

import cn.sutone.ai.domain.content.model.entity.ArticleEntity;

import java.util.List;

/**
 * 发布领域服务接口
 */
public interface IPublishDomainService {

    ArticleEntity publish(Long userId, Long draftId, List<String> tags);

}
