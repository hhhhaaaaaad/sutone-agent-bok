package cn.sutone.ai.domain.content.adapter.repository;

import java.util.List;

public interface IFollowRepository {

    void save(Long followerId, Long followeeId);

    void remove(Long followerId, Long followeeId);

    boolean exists(Long followerId, Long followeeId);

    List<Long> findFollowingIds(Long userId);

    List<Long> findFollowerIds(Long userId);

    int countFollowers(Long userId);

    int countFollowing(Long userId);
}
