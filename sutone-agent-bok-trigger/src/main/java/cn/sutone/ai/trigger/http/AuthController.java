package cn.sutone.ai.trigger.http;

import cn.sutone.ai.api.dto.auth.LoginRequestDTO;
import cn.sutone.ai.api.dto.auth.LoginResponseDTO;
import cn.sutone.ai.api.dto.auth.RegisterRequestDTO;
import cn.sutone.ai.api.dto.auth.RegisterResponseDTO;
import cn.sutone.ai.api.response.Response;
import cn.sutone.ai.domain.account.model.entity.UserEntity;
import cn.sutone.ai.domain.account.service.IUserDomainService;
import cn.sutone.ai.trigger.security.JwtTokenProvider;
import cn.sutone.ai.types.enums.ResponseCode;
import cn.sutone.ai.types.exception.AppException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final IUserDomainService userDomainService;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(IUserDomainService userDomainService, JwtTokenProvider jwtTokenProvider) {
        this.userDomainService = userDomainService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/login")
    public Response<LoginResponseDTO> login(@RequestBody LoginRequestDTO requestDTO,
                                            HttpServletResponse httpResponse) {
        try {
            log.info("用户登录 username:{}", requestDTO.getUsername());

            UserEntity user = userDomainService.login(
                    requestDTO.getUsername(), requestDTO.getPassword());
            String token = jwtTokenProvider.generateToken(user.getUserId(), user.getUsername());

            Cookie cookie = new Cookie("token", token);
            cookie.setHttpOnly(true);
            cookie.setSecure(false);
            cookie.setPath("/");
            cookie.setMaxAge(7200);
            cookie.setAttribute("SameSite", "Lax");
            httpResponse.addCookie(cookie);

            return Response.<LoginResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(LoginResponseDTO.builder()
                            .userId(user.getUserId())
                            .username(user.getUsername())
                            .nickname(user.getNickname())
                            .build())
                    .build();
        } catch (AppException e) {
            log.error("登录失败 username:{}", requestDTO.getUsername(), e);
            return fail(e);
        } catch (Exception e) {
            log.error("登录异常 username:{}", requestDTO.getUsername(), e);
            return fail(e);
        }
    }

    @PostMapping("/logout")
    public Response<Void> logout(HttpServletResponse httpResponse) {
        Cookie cookie = new Cookie("token", "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        httpResponse.addCookie(cookie);
        return Response.<Void>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info("已退出登录")
                .build();
    }

    @PostMapping("/register")
    public Response<RegisterResponseDTO> register(@RequestBody RegisterRequestDTO requestDTO) {
        try {
            log.info("用户注册 username:{}", requestDTO.getUsername());

            UserEntity user = userDomainService.register(
                    requestDTO.getUsername(),
                    requestDTO.getPassword(),
                    requestDTO.getNickname());

            return Response.<RegisterResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(RegisterResponseDTO.builder()
                            .userId(user.getUserId())
                            .username(user.getUsername())
                            .build())
                    .build();
        } catch (AppException e) {
            log.error("注册失败 username:{}", requestDTO.getUsername(), e);
            return fail(e);
        } catch (Exception e) {
            log.error("注册异常 username:{}", requestDTO.getUsername(), e);
            return fail(e);
        }
    }

    private <T> Response<T> fail(Exception e) {
        String code = ResponseCode.UN_ERROR.getCode();
        String info = e.getMessage();
        if (e instanceof AppException ae) {
            code = ae.getCode() != null ? ae.getCode() : code;
            info = ae.getInfo() != null ? ae.getInfo() : info;
        }
        return Response.<T>builder().code(code).info(info).build();
    }
}
