package cn.sutone.ai.types.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationItemDTO {

    private Long id;
    private String type;
    private Long senderId;
    private String senderName;
    private String avatarUrl;
    private Long refId;
    private String content;
    private Boolean isRead;
    private String createTime;
}
