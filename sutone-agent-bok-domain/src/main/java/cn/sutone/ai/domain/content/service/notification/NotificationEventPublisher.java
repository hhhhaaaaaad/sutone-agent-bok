package cn.sutone.ai.domain.content.service.notification;

import cn.sutone.ai.domain.account.adapter.repository.IUserRepository;
import cn.sutone.ai.domain.account.model.entity.UserEntity;
import cn.sutone.ai.domain.content.service.INotificationDomainService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationEventPublisher {

    private final INotificationDomainService notificationService;
    private final IUserRepository userRepository;

    public NotificationEventPublisher(INotificationDomainService notificationService,
                                       IUserRepository userRepository) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @Async
    public void publish(Long userId, String type, Long senderId, Long refId, String action) {
        try {
            UserEntity sender = userRepository.findById(senderId);
            String senderName = sender != null ? sender.getUsername() : ("用户#" + senderId);
            String content = senderName + " " + action;
            notificationService.send(userId, type, senderId, refId, content);
        } catch (Exception e) {
            log.warn("通知发送失败 userId:{} type:{} refId:{}", userId, type, refId, e);
        }
    }
}
