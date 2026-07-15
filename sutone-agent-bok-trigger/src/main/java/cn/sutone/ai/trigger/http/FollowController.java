package cn.sutone.ai.trigger.http;

import cn.sutone.ai.api.response.Response;
import cn.sutone.ai.domain.content.service.IFollowDomainService;
import cn.sutone.ai.trigger.security.AuthUtil;
import cn.sutone.ai.types.enums.ResponseCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/")
@CrossOrigin(origins = "*")
public class FollowController {

    @Resource
    private IFollowDomainService followService;

    /** 关注用户 */
    @PostMapping("user/follow/{userId}")
    public Response<Void> follow(@PathVariable Long userId) {
        try {
            Long currentUserId = AuthUtil.getCurrentUserId();
            followService.follow(currentUserId, userId);
            return Response.<Void>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("关注失败 userId:{}", userId, e);
            return fail(e);
        }
    }

    /** 取消关注 */
    @DeleteMapping("user/follow/{userId}")
    public Response<Void> unfollow(@PathVariable Long userId) {
        try {
            Long currentUserId = AuthUtil.getCurrentUserId();
            followService.unfollow(currentUserId, userId);
            return Response.<Void>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("取消关注失败 userId:{}", userId, e);
            return fail(e);
        }
    }

    /** 查询关注状态 */
    @GetMapping("user/{userId}/follow/status")
    public Response<Map<String, Object>> followStatus(@PathVariable Long userId) {
        try {
            Long currentUserId = AuthUtil.getCurrentUserIdOrNull();
            boolean following = currentUserId != null && followService.isFollowing(currentUserId, userId);
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(Map.of("following", following))
                    .build();
        } catch (Exception e) {
            log.error("查询关注状态失败 userId:{}", userId, e);
            return fail(e);
        }
    }

    /** 粉丝列表 */
    @GetMapping("user/{userId}/followers")
    public Response<Map<String, Object>> followers(@PathVariable Long userId) {
        try {
            List<Long> ids = followService.getFollowers(userId);
            int count = followService.getFollowersCount(userId);
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(Map.of("list", ids, "total", count))
                    .build();
        } catch (Exception e) {
            log.error("查询粉丝列表失败 userId:{}", userId, e);
            return fail(e);
        }
    }

    /** 关注列表 */
    @GetMapping("user/{userId}/following")
    public Response<Map<String, Object>> following(@PathVariable Long userId) {
        try {
            List<Long> ids = followService.getFollowing(userId);
            int count = followService.getFollowingCount(userId);
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(Map.of("list", ids, "total", count))
                    .build();
        } catch (Exception e) {
            log.error("查询关注列表失败 userId:{}", userId, e);
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
