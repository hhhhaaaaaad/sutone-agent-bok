package cn.sutone.ai.domain.content.service.dashboard;

import cn.sutone.ai.domain.content.adapter.repository.IArticleRepository;
import cn.sutone.ai.domain.content.adapter.repository.ICommentRepository;
import cn.sutone.ai.domain.content.adapter.repository.ISocialRepository;
import cn.sutone.ai.domain.content.model.entity.ArticleEntity;
import cn.sutone.ai.domain.content.service.ICommentDomainService;
import cn.sutone.ai.domain.content.service.IDashboardService;
import cn.sutone.ai.domain.content.service.ISocialDomainService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DashboardService implements IDashboardService {

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final IArticleRepository articleRepository;
    private final ISocialDomainService socialService;
    private final ICommentDomainService commentService;
    private final ISocialRepository socialRepository;
    private final ICommentRepository commentRepository;

    public DashboardService(IArticleRepository articleRepository,
                            ISocialDomainService socialService,
                            ICommentDomainService commentService,
                            ISocialRepository socialRepository,
                            ICommentRepository commentRepository) {
        this.articleRepository = articleRepository;
        this.socialService = socialService;
        this.commentService = commentService;
        this.socialRepository = socialRepository;
        this.commentRepository = commentRepository;
    }

    public Map<String, Object> getOverview(Long userId) {
        List<ArticleEntity> articles = articleRepository.queryArticlePage(1, 1000, userId, null);
        int articleCount = articles.size();
        long totalViews = 0;
        long totalLikes = 0;
        long totalFavorites = 0;
        long totalComments = 0;

        for (ArticleEntity a : articles) {
            Long id = a.getArticleId();
            if (a.getMeta() != null) {
                totalViews += a.getMeta().getViewCount() != null ? a.getMeta().getViewCount() : 0;
            }
            totalLikes += socialService.getLikeCount(id);
            totalFavorites += socialService.getFavoriteCount(id);
            totalComments += commentService.getCommentCount(id);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("articleCount", articleCount);
        result.put("totalViews", totalViews);
        result.put("totalLikes", totalLikes);
        result.put("totalFavorites", totalFavorites);
        result.put("totalComments", totalComments);
        return result;
    }

    public List<Map<String, Object>> getTrend(Long userId, int days) {
        LocalDate since = LocalDate.now().minusDays(days);
        String sinceStr = since.format(DF);

        Map<String, Long> dailyLikes = toDailyMap(socialRepository.countDailyLikesByAuthor(userId, sinceStr));
        Map<String, Long> dailyFavorites = toDailyMap(socialRepository.countDailyFavoritesByAuthor(userId, sinceStr));
        Map<String, Long> dailyComments = toDailyMap(commentRepository.countDailyByAuthor(userId, sinceStr));

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            String key = date.format(DF);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", key);
            point.put("likes", dailyLikes.getOrDefault(key, 0L));
            point.put("favorites", dailyFavorites.getOrDefault(key, 0L));
            point.put("comments", dailyComments.getOrDefault(key, 0L));
            result.add(point);
        }
        return result;
    }

    private Map<String, Long> toDailyMap(List<Map<String, Object>> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String d = row.get("date").toString();
            Long cnt = ((Number) row.get("cnt")).longValue();
            map.put(d, cnt);
        }
        return map;
    }
}
