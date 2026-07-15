package cn.sutone.ai.domain.content.service;

import java.util.List;

public interface IFollowDomainService {

    void follow(Long followerId, Long followeeId);

    void unfollow(Long followerId, Long followeeId);

    boolean isFollowing(Long followerId, Long followeeId);

    List<Long> getFollowing(Long userId);

    List<Long> getFollowers(Long userId);

    int getFollowingCount(Long userId);

    int getFollowersCount(Long userId);
}
