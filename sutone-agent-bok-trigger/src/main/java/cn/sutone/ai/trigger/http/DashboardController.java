package cn.sutone.ai.trigger.http;

import cn.sutone.ai.api.response.Response;
import cn.sutone.ai.domain.content.service.IDashboardService;
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
public class DashboardController {

    @Resource
    private IDashboardService dashboardService;

    /** 总览 */
    @GetMapping("dashboard/overview")
    public Response<Map<String, Object>> overview() {
        try {
            Long userId = AuthUtil.getCurrentUserId();
            Map<String, Object> result = dashboardService.getOverview(userId);
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result)
                    .build();
        } catch (Exception e) {
            log.error("查询仪表盘总览失败", e);
            return fail(e);
        }
    }

    /** 趋势 */
    @GetMapping("dashboard/trend")
    public Response<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "7") int days) {
        try {
            Long userId = AuthUtil.getCurrentUserId();
            List<Map<String, Object>> result = dashboardService.getTrend(userId, days);
            return Response.<List<Map<String, Object>>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result)
                    .build();
        } catch (Exception e) {
            log.error("查询仪表盘趋势失败", e);
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
