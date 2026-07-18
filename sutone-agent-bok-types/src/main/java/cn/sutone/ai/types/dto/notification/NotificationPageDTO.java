package cn.sutone.ai.types.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPageDTO {
    private List<NotificationItemDTO> list;
    private int total;
    private int page;
    private int pageSize;
}
