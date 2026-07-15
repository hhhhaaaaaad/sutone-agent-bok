package cn.sutone.ai.domain.content.service;

import cn.sutone.ai.types.dto.notification.NotificationItemDTO;
import cn.sutone.ai.types.dto.notification.NotificationPageDTO;

public interface INotificationDomainService {

    void send(Long userId, String type, Long senderId, Long refId, String content);

    NotificationPageDTO queryPage(Long userId, int page, int pageSize);

    int getUnreadCount(Long userId);

    void markRead(Long id, Long userId);

    void markAllRead(Long userId);
}
