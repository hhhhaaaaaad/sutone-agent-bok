package cn.sutone.ai.infrastructure.adapter.repository;

import cn.sutone.ai.domain.account.adapter.repository.IUserRepository;
import cn.sutone.ai.domain.account.model.entity.UserEntity;
import cn.sutone.ai.infrastructure.dao.IUserDao;
import cn.sutone.ai.infrastructure.dao.po.UserPO;
import org.springframework.stereotype.Repository;

/**
 * 用户仓储实现
 */
@Repository
public class UserRepository implements IUserRepository {

    private final IUserDao userDao;

    public UserRepository(IUserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public UserEntity findByUsername(String username) {
        return toEntity(userDao.selectByUsername(username));
    }

    @Override
    public UserEntity findById(Long userId) {
        return toEntity(userDao.selectById(userId));
    }

    @Override
    public void save(UserEntity userEntity) {
        userDao.insert(toUserPO(userEntity));
    }

    private UserPO toUserPO(UserEntity entity) {
        return UserPO.builder()
                .id(entity.getUserId())
                .username(entity.getUsername())
                .passwordHash(entity.getPasswordHash())
                .nickname(entity.getNickname())
                .avatarUrl(entity.getAvatarUrl())
                .build();
    }

    private UserEntity toEntity(UserPO po) {
        if (null == po) {
            return null;
        }
        return UserEntity.builder()
                .userId(po.getId())
                .username(po.getUsername())
                .passwordHash(po.getPasswordHash())
                .nickname(po.getNickname())
                .avatarUrl(po.getAvatarUrl())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }
}
