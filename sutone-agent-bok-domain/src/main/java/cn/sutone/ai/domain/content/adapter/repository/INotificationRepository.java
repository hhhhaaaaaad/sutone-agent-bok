package cn.sutone.ai.domain.content.adapter.repository;

import java.time.LocalDateTime;
import java.util.List;

public interface INotificationRepository {

    void save(Long userId, String type, Long senderId, Long refId, String content);

    List<NotificationRow> queryByUserId(Long userId, int page, int pageSize);

    int countByUserId(Long userId);

    int countUnread(Long userId);

    int markRead(Long id, Long userId);

    int markAllRead(Long userId);

    /** 查询结果行（domain 层不依赖 PO） */
    record NotificationRow(Long id, Long userId, String type, Long senderId, Long refId,
                           String content, Integer isRead, LocalDateTime createTime) {}
}
