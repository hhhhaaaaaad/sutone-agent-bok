package cn.sutone.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 统一分页响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponseDTO<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer total;
    private Integer pageNo;
    private Integer pageSize;
    private List<T> list;

    public static <T> PageResponseDTO<T> empty(Integer pageNo, Integer pageSize) {
        return PageResponseDTO.<T>builder()
                .total(0)
                .pageNo(pageNo)
                .pageSize(pageSize)
                .list(Collections.emptyList())
                .build();
    }
}
