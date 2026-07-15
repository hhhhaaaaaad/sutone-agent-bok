package cn.sutone.ai.domain.content.service.recommend;

import cn.sutone.ai.domain.content.adapter.repository.IArticleRepository;
import cn.sutone.ai.domain.content.model.entity.ArticleEntity;
import cn.sutone.ai.domain.content.service.ISocialDomainService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class RecommendService {

    private final ISocialDomainService socialService;
    private final IArticleRepository articleRepository;

    public RecommendService(ISocialDomainService socialService, IArticleRepository articleRepository) {
        this.socialService = socialService;
        this.articleRepository = articleRepository;
    }

    public List<ArticleEntity> recommend(Long userId, int n) {
        // 1. 收集用户互动过的文章ID
        Set<Long> interactedIds = collectInteractedIds(userId);

        // 2. 提取 Top 5 高频标签
        List<String> topTags = extractTopTags(interactedIds, 5);

        // 3. 标签匹配候选
        List<Long> candidateIds = new ArrayList<>();
        if (!topTags.isEmpty()) {
            candidateIds = articleRepository.queryIdsByTags(topTags,
                    new ArrayList<>(interactedIds), n);
        }

        // 4. 候选不足 → 排行榜兜底
        if (candidateIds.size() < n) {
            Set<Long> existing = new LinkedHashSet<>(candidateIds);
            Map<Long, Double> hot = socialService.getTopN("daily", n * 2);
            for (Long hotId : hot.keySet()) {
                if (existing.size() >= n) break;
                if (!interactedIds.contains(hotId)) {
                    existing.add(hotId);
                }
            }
            candidateIds = new ArrayList<>(existing);
        }

        // 5. 批量查文章详情
        if (candidateIds.isEmpty()) {
            return List.of();
        }
        return articleRepository.queryByIds(candidateIds.subList(0, Math.min(candidateIds.size(), n)));
    }

    private Set<Long> collectInteractedIds(Long userId) {
        Set<Long> ids = new LinkedHashSet<>();
        try {
            ids.addAll(socialService.getUserLikes(userId));
        } catch (Exception ignored) {}
        try {
            ids.addAll(socialService.getUserFavorites(userId));
        } catch (Exception ignored) {}
        return ids;
    }

    private List<String> extractTopTags(Set<Long> articleIds, int topN) {
        if (articleIds.isEmpty()) return List.of();
        Map<String, Integer> tagCounts = new HashMap<>();
        for (Long id : articleIds) {
            try {
                ArticleEntity article = articleRepository.queryArticleById(id);
                if (article != null && article.getMeta() != null && article.getMeta().getTags() != null) {
                    for (String tag : article.getMeta().getTags()) {
                        tagCounts.merge(tag.trim().toLowerCase(), 1, Integer::sum);
                    }
                }
            } catch (Exception ignored) {}
        }
        return tagCounts.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(topN)
                .map(Map.Entry::getKey)
                .toList();
    }
}
