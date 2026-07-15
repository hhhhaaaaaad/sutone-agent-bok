package cn.sutone.ai.infrastructure.adapter.repository;

import cn.sutone.ai.domain.content.adapter.repository.IFollowRepository;
import cn.sutone.ai.infrastructure.dao.IUserFollowDao;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class FollowRepository implements IFollowRepository {

    private final IUserFollowDao followDao;

    public FollowRepository(IUserFollowDao followDao) {
        this.followDao = followDao;
    }

    @Override
    public void save(Long followerId, Long followeeId) {
        followDao.insert(followerId, followeeId);
    }

    @Override
    public void remove(Long followerId, Long followeeId) {
        followDao.delete(followerId, followeeId);
    }

    @Override
    public boolean exists(Long followerId, Long followeeId) {
        return followDao.exists(followerId, followeeId);
    }

    @Override
    public List<Long> findFollowingIds(Long userId) {
        return followDao.findFollowingIds(userId);
    }

    @Override
    public List<Long> findFollowerIds(Long userId) {
        return followDao.findFollowerIds(userId);
    }

    @Override
    public int countFollowers(Long userId) {
        return followDao.countFollowers(userId);
    }

    @Override
    public int countFollowing(Long userId) {
        return followDao.countFollowing(userId);
    }
}
