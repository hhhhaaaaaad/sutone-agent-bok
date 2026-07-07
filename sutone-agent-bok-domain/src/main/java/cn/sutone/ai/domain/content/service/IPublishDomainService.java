package cn.sutone.ai.domain.content.service;

import cn.sutone.ai.domain.content.model.entity.ArticleEntity;
import cn.sutone.ai.domain.content.model.entity.DraftEntity;

import java.util.List;

/**
 * 发布领域服务接口
 */
public interface IPublishDomainService {

    ArticleEntity publish(Long userId, Long draftId, List<String> tags);

    /**
     * 将已发布文章回退到草稿编辑状态
     * 将原草稿状态重置为 EDITING，可选同时下架文章
     *
     * @param userId    用户ID
     * @param articleId 文章ID
     * @return 回退后的草稿
     */
    DraftEntity revertToDraft(Long userId, Long articleId);

}
