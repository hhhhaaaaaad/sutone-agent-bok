package cn.sutone.ai.domain.account.service;

import cn.sutone.ai.domain.account.model.entity.UserEntity;

/**
 * 用户领域服务接口
 */
public interface IUserDomainService {

    UserEntity login(String username, String rawPassword);

    UserEntity register(String username, String rawPassword, String nickname);

    UserEntity queryById(Long userId);
}
