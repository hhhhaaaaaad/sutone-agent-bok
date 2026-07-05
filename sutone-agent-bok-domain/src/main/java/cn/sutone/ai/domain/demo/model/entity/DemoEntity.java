package cn.sutone.ai.domain.demo.model.entity;

import cn.sutone.ai.domain.demo.model.valobj.DemoStatusVO;
import cn.sutone.ai.types.enums.ResponseCode;
import cn.sutone.ai.types.exception.AppException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Demo 主实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoEntity {

    private Long id;
    private String demoCode;
    private String demoName;
    private DemoStatusVO status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static DemoEntity init(Long id, String demoCode, String demoName) {
        LocalDateTime now = LocalDateTime.now();
        return DemoEntity.builder()
                .id(id)
                .demoCode(demoCode)
                .demoName(demoName)
                .status(DemoStatusVO.INIT)
                .createTime(now)
                .updateTime(now)
                .build();
    }

    public void rename(String demoName) {
        if (null == demoName || demoName.isBlank()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "demoName 不能为空");
        }
        this.demoName = demoName;
        this.updateTime = LocalDateTime.now();
    }

    public void enable() {
        this.status = DemoStatusVO.ENABLED;
        this.updateTime = LocalDateTime.now();
    }

    public void disable() {
        this.status = DemoStatusVO.DISABLED;
        this.updateTime = LocalDateTime.now();
    }
}
