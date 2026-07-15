package cn.sutone.ai.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPO {

    private Long id;
    private Long userId;
    private String type;
    private Long senderId;
    private Long refId;
    private String content;
    private Integer isRead;
    private LocalDateTime createTime;
}
