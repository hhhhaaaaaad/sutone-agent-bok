package cn.sutone.ai.domain.content.service.follow;

import cn.sutone.ai.domain.content.adapter.repository.IFollowRepository;
import cn.sutone.ai.domain.content.service.IFollowDomainService;
import cn.sutone.ai.domain.content.service.notification.NotificationEventPublisher;
import cn.sutone.ai.types.common.RedisKeyConstants;
import cn.sutone.ai.types.exception.AppException;
import cn.sutone.ai.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class FollowDomainService implements IFollowDomainService {

    private final IFollowRepository followRepository;
    private final RedissonClient redissonClient;
    private final NotificationEventPublisher notificationPublisher;

    public FollowDomainService(IFollowRepository followRepository, RedissonClient redissonClient,
                               NotificationEventPublisher notificationPublisher) {
        this.followRepository = followRepository;
        this.redissonClient = redissonClient;
        this.notificationPublisher = notificationPublisher;
    }

    @Override
    @Transactional
    public void follow(Long followerId, Long followeeId) {
        if (followerId.equals(followeeId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "不能关注自己");
        }
        followRepository.save(followerId, followeeId);
        try {
            RSet<Long> followingSet = redissonClient.getSet(RedisKeyConstants.USER_FOLLOWING_PREFIX + followerId);
            followingSet.add(followeeId);
            RSet<Long> followersSet = redissonClient.getSet(RedisKeyConstants.USER_FOLLOWERS_PREFIX + followeeId);
            followersSet.add(followerId);
            RAtomicLong followingCounter = redissonClient.getAtomicLong(
                    RedisKeyConstants.USER_FOLLOWING_COUNT_PREFIX + followerId);
            followingCounter.incrementAndGet();
            RAtomicLong followersCounter = redissonClient.getAtomicLong(
                    RedisKeyConstants.USER_FOLLOWERS_COUNT_PREFIX + followeeId);
            followersCounter.incrementAndGet();
        } catch (Exception e) {
            log.warn("Redis cache update failed, evicting follow keys follower:{}, followee:{}", followerId, followeeId, e);
            redissonClient.getSet(RedisKeyConstants.USER_FOLLOWING_PREFIX + followerId).delete();
            redissonClient.getSet(RedisKeyConstants.USER_FOLLOWERS_PREFIX + followeeId).delete();
            redissonClient.getAtomicLong(RedisKeyConstants.USER_FOLLOWING_COUNT_PREFIX + followerId).delete();
            redissonClient.getAtomicLong(RedisKeyConstants.USER_FOLLOWERS_COUNT_PREFIX + followeeId).delete();
        }
        notificationPublisher.publish(followeeId, "NEW_FOLLOW", followerId, null, "关注了你");
    }

    @Override
    @Transactional
    public void unfollow(Long followerId, Long followeeId) {
        followRepository.remove(followerId, followeeId);
        try {
            RSet<Long> followingSet = redissonClient.getSet(RedisKeyConstants.USER_FOLLOWING_PREFIX + followerId);
            followingSet.remove(followeeId);
            RSet<Long> followersSet = redissonClient.getSet(RedisKeyConstants.USER_FOLLOWERS_PREFIX + followeeId);
            followersSet.remove(followerId);
            RAtomicLong followingCounter = redissonClient.getAtomicLong(
                    RedisKeyConstants.USER_FOLLOWING_COUNT_PREFIX + followerId);
            if (followingCounter.isExists() && followingCounter.get() > 0) {
                followingCounter.decrementAndGet();
            }
            RAtomicLong followersCounter = redissonClient.getAtomicLong(
                    RedisKeyConstants.USER_FOLLOWERS_COUNT_PREFIX + followeeId);
            if (followersCounter.isExists() && followersCounter.get() > 0) {
                followersCounter.decrementAndGet();
            }
        } catch (Exception e) {
            log.warn("Redis cache update failed, evicting follow keys follower:{}, followee:{}", followerId, followeeId, e);
            redissonClient.getSet(RedisKeyConstants.USER_FOLLOWING_PREFIX + followerId).delete();
            redissonClient.getSet(RedisKeyConstants.USER_FOLLOWERS_PREFIX + followeeId).delete();
            redissonClient.getAtomicLong(RedisKeyConstants.USER_FOLLOWING_COUNT_PREFIX + followerId).delete();
            redissonClient.getAtomicLong(RedisKeyConstants.USER_FOLLOWERS_COUNT_PREFIX + followeeId).delete();
        }
    }

    @Override
    public boolean isFollowing(Long followerId, Long followeeId) {
        RSet<Long> set = redissonClient.getSet(RedisKeyConstants.USER_FOLLOWING_PREFIX + followerId);
        if (set.isExists()) {
            return set.contains(followeeId);
        }
        boolean exists = followRepository.exists(followerId, followeeId);
        if (exists) {
            set.add(followeeId);
        }
        return exists;
    }

    @Override
    public List<Long> getFollowing(Long userId) {
        RSet<Long> set = redissonClient.getSet(RedisKeyConstants.USER_FOLLOWING_PREFIX + userId);
        if (set.isExists()) {
            return new ArrayList<>(set.readAll());
        }
        List<Long> ids = followRepository.findFollowingIds(userId);
        if (!ids.isEmpty()) {
            set.addAll(ids);
        }
        return ids;
    }

    @Override
    public List<Long> getFollowers(Long userId) {
        RSet<Long> set = redissonClient.getSet(RedisKeyConstants.USER_FOLLOWERS_PREFIX + userId);
        if (set.isExists()) {
            return new ArrayList<>(set.readAll());
        }
        List<Long> ids = followRepository.findFollowerIds(userId);
        if (!ids.isEmpty()) {
            set.addAll(ids);
        }
        return ids;
    }

    @Override
    public int getFollowingCount(Long userId) {
        RAtomicLong counter = redissonClient.getAtomicLong(
                RedisKeyConstants.USER_FOLLOWING_COUNT_PREFIX + userId);
        if (counter.isExists()) {
            return (int) counter.get();
        }
        int dbCount = followRepository.countFollowing(userId);
        counter.set(dbCount);
        return dbCount;
    }

    @Override
    public int getFollowersCount(Long userId) {
        RAtomicLong counter = redissonClient.getAtomicLong(
                RedisKeyConstants.USER_FOLLOWERS_COUNT_PREFIX + userId);
        if (counter.isExists()) {
            return (int) counter.get();
        }
        int dbCount = followRepository.countFollowers(userId);
        counter.set(dbCount);
        return dbCount;
    }
}
