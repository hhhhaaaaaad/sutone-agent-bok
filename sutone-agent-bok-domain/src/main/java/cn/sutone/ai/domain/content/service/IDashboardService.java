package cn.sutone.ai.domain.content.service;

import java.util.List;
import java.util.Map;

/**
 * 仪表盘领域服务接口
 */
public interface IDashboardService {

    Map<String, Object> getOverview(Long userId);

    List<Map<String, Object>> getTrend(Long userId, int days);
}
