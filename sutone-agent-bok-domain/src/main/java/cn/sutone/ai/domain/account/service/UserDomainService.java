package cn.sutone.ai.domain.account.service;

import cn.sutone.ai.domain.account.adapter.repository.IUserRepository;
import cn.sutone.ai.domain.account.model.entity.UserEntity;
import cn.sutone.ai.types.enums.ResponseCode;
import cn.sutone.ai.types.exception.AppException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户领域服务
 */
@Service
public class UserDomainService implements IUserDomainService {

    private final IUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserDomainService(IUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserEntity login(String username, String rawPassword) {
        UserEntity user = userRepository.findByUsername(username);
        if (null == user) {
            throw new AppException(ResponseCode.UNAUTHORIZED.getCode(), "用户名或密码错误");
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new AppException(ResponseCode.UNAUTHORIZED.getCode(), "用户名或密码错误");
        }
        return user;
    }

    public UserEntity register(String username, String rawPassword, String nickname) {
        UserEntity existing = userRepository.findByUsername(username);
        if (null != existing) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "用户名已存在");
        }
        UserEntity user = UserEntity.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .nickname(null == nickname || nickname.isBlank() ? username : nickname)
                .build();
        userRepository.save(user);
        return user;
    }

    public UserEntity queryById(Long userId) {
        return userRepository.findById(userId);
    }
}
