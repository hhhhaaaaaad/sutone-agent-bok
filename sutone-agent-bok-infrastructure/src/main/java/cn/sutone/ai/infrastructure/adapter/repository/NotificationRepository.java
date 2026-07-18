package cn.sutone.ai.infrastructure.adapter.repository;

import cn.sutone.ai.domain.content.adapter.repository.INotificationRepository;
import cn.sutone.ai.infrastructure.dao.INotificationDao;
import cn.sutone.ai.infrastructure.dao.po.NotificationPO;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class NotificationRepository implements INotificationRepository {

    private final INotificationDao notificationDao;

    public NotificationRepository(INotificationDao notificationDao) {
        this.notificationDao = notificationDao;
    }

    @Override
    public void save(Long userId, String type, Long senderId, Long refId, String content) {
        NotificationPO po = NotificationPO.builder()
                .userId(userId).type(type).senderId(senderId).refId(refId).content(content).build();
        notificationDao.insert(po);
    }

    @Override
    public List<NotificationRow> queryByUserId(Long userId, int page, int pageSize) {
        int offset = Math.max(page - 1, 0) * pageSize;
        return notificationDao.queryByUserId(userId, offset, pageSize).stream()
                .map(po -> new NotificationRow(po.getId(), po.getUserId(), po.getType(),
                        po.getSenderId(), po.getRefId(), po.getContent(), po.getIsRead(), po.getCreateTime()))
                .toList();
    }

    @Override
    public int countByUserId(Long userId) {
        return notificationDao.countByUserId(userId);
    }

    @Override
    public int countUnread(Long userId) {
        return notificationDao.countUnread(userId);
    }

    @Override
    public int markRead(Long id, Long userId) {
        return notificationDao.markRead(id, userId);
    }

    @Override
    public int markAllRead(Long userId) {
        return notificationDao.markAllRead(userId);
    }
}
