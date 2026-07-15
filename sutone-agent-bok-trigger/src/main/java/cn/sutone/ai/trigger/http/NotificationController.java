package cn.sutone.ai.trigger.http;

import cn.sutone.ai.api.response.Response;
import cn.sutone.ai.domain.content.service.INotificationDomainService;
import cn.sutone.ai.trigger.security.AuthUtil;
import cn.sutone.ai.types.dto.notification.NotificationPageDTO;
import cn.sutone.ai.types.enums.ResponseCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/")
@CrossOrigin(origins = "*")
public class NotificationController {

    @Resource
    private INotificationDomainService notificationService;

    /** 分页查询通知列表 */
    @GetMapping("notifications")
    public Response<NotificationPageDTO> list(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int pageSize) {
        try {
            Long userId = AuthUtil.getCurrentUserId();
            NotificationPageDTO result = notificationService.queryPage(userId, page, pageSize);
            return Response.<NotificationPageDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result)
                    .build();
        } catch (Exception e) {
            log.error("查询通知列表失败", e);
            return fail(e);
        }
    }

    /** 获取未读通知数 */
    @GetMapping("notifications/unread-count")
    public Response<Map<String, Object>> unreadCount() {
        try {
            Long userId = AuthUtil.getCurrentUserId();
            int count = notificationService.getUnreadCount(userId);
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(Map.of("unreadCount", count))
                    .build();
        } catch (Exception e) {
            log.error("查询未读通知数失败", e);
            return fail(e);
        }
    }

    /** 标记单条已读 */
    @PutMapping("notifications/{id}/read")
    public Response<Void> markRead(@PathVariable Long id) {
        try {
            Long userId = AuthUtil.getCurrentUserId();
            notificationService.markRead(id, userId);
            return Response.<Void>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("标记通知已读失败 id:{}", id, e);
            return fail(e);
        }
    }

    /** 全部标记已读 */
    @PutMapping("notifications/read-all")
    public Response<Void> markAllRead() {
        try {
            Long userId = AuthUtil.getCurrentUserId();
            notificationService.markAllRead(userId);
            return Response.<Void>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("全部标记已读失败", e);
            return fail(e);
        }
    }

    private <T> Response<T> fail(Exception e) {
        String code = ResponseCode.UN_ERROR.getCode();
        String info = e.getMessage();
        if (e instanceof cn.sutone.ai.types.exception.AppException ae) {
            code = ae.getCode() != null ? ae.getCode() : code;
            info = ae.getInfo() != null ? ae.getInfo() : info;
        }
        return Response.<T>builder().code(code).info(info).build();
    }
}
