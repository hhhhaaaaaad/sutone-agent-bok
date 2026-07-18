package cn.sutone.ai.domain.content.service.notification;

import cn.sutone.ai.domain.content.adapter.repository.INotificationRepository;
import cn.sutone.ai.domain.content.adapter.repository.INotificationRepository.NotificationRow;
import cn.sutone.ai.domain.content.service.INotificationDomainService;
import cn.sutone.ai.types.common.NotificationConstants;
import cn.sutone.ai.types.dto.notification.NotificationItemDTO;
import cn.sutone.ai.types.dto.notification.NotificationPageDTO;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class NotificationService implements INotificationDomainService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final INotificationRepository notificationRepository;
    private final RedissonClient redissonClient;

    public NotificationService(INotificationRepository notificationRepository, RedissonClient redissonClient) {
        this.notificationRepository = notificationRepository;
        this.redissonClient = redissonClient;
    }

    @Override
    public void send(Long userId, String type, Long senderId, Long refId, String content) {
        notificationRepository.save(userId, type, senderId, refId, content);
        RAtomicLong counter = redissonClient.getAtomicLong(NotificationConstants.UNREAD_COUNT_PREFIX + userId);
        counter.incrementAndGet();
    }

    @Override
    public NotificationPageDTO queryPage(Long userId, int page, int pageSize) {
        List<NotificationRow> rows = notificationRepository.queryByUserId(userId, page, pageSize);
        int total = notificationRepository.countByUserId(userId);
        List<NotificationItemDTO> list = new ArrayList<>();
        for (NotificationRow row : rows) {
            list.add(NotificationItemDTO.builder()
                    .id(row.id()).type(row.type()).senderId(row.senderId())
                    .refId(row.refId()).content(row.content())
                    .isRead(row.isRead() != null && row.isRead() == 1)
                    .createTime(row.createTime() != null ? row.createTime().format(DTF) : null)
                    .build());
        }
        return NotificationPageDTO.builder().list(list).total(total).page(page).pageSize(pageSize).build();
    }

    @Override
    public int getUnreadCount(Long userId) {
        RAtomicLong counter = redissonClient.getAtomicLong(NotificationConstants.UNREAD_COUNT_PREFIX + userId);
        if (counter.isExists()) {
            return (int) counter.get();
        }
        int dbCount = notificationRepository.countUnread(userId);
        counter.set(dbCount);
        return dbCount;
    }

    @Override
    public void markRead(Long id, Long userId) {
        notificationRepository.markRead(id, userId);
        RAtomicLong counter = redissonClient.getAtomicLong(NotificationConstants.UNREAD_COUNT_PREFIX + userId);
        if (counter.isExists() && counter.get() > 0) {
            counter.decrementAndGet();
        }
    }

    @Override
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId);
        redissonClient.getAtomicLong(NotificationConstants.UNREAD_COUNT_PREFIX + userId).delete();
    }
}
