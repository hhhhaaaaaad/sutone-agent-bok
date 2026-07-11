package cn.sutone.ai.domain.account.adapter.repository;

import cn.sutone.ai.domain.account.model.entity.UserEntity;

/**
 * 用户仓储接口
 */
public interface IUserRepository {

    UserEntity findByUsername(String username);

    UserEntity findById(Long userId);

    void save(UserEntity userEntity);
}
