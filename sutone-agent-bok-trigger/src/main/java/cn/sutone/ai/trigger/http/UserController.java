package cn.sutone.ai.trigger.http;

import cn.sutone.ai.api.response.Response;
import cn.sutone.ai.domain.account.adapter.repository.IUserRepository;
import cn.sutone.ai.domain.account.model.entity.UserEntity;
import cn.sutone.ai.types.enums.ResponseCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/")
@CrossOrigin(origins = "*")
public class UserController {

    @Resource
    private IUserRepository userRepository;

    @GetMapping("user/{id}")
    public Response<Map<String, Object>> getUserById(@PathVariable Long id) {
        try {
            UserEntity user = userRepository.findById(id);
            if (user == null) {
                return Response.<Map<String, Object>>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("用户不存在")
                        .build();
            }
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(Map.of("userId", user.getUserId(), "username", user.getUsername()))
                    .build();
        } catch (Exception e) {
            log.error("查询用户失败 id:{}", id, e);
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(e.getMessage())
                    .build();
        }
    }
}
