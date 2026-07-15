package cn.sutone.ai.infrastructure.dao;

import cn.sutone.ai.infrastructure.dao.po.ArticleViewDailyPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface IArticleViewDailyDao {

    @Insert("""
            INSERT INTO article_view_daily(article_id, date, view_count, like_count, favorite_count)
            VALUES(#{articleId}, #{date}, #{viewCount}, #{likeCount}, #{favoriteCount})
            ON DUPLICATE KEY UPDATE view_count = VALUES(view_count),
                                    like_count = VALUES(like_count),
                                    favorite_count = VALUES(favorite_count)
            """)
    int upsert(ArticleViewDailyPO po);

    @Select("""
            SELECT id, article_id, date, view_count, like_count, favorite_count
            FROM article_view_daily
            WHERE article_id IN
            <foreach collection='articleIds' item='id' open='(' separator=',' close=')'>
                #{id}
            </foreach>
            AND date >= #{since}
            ORDER BY date ASC
            """)
    List<ArticleViewDailyPO> queryByArticleIds(@Param("articleIds") List<Long> articleIds,
                                                @Param("since") LocalDate since);

    @Insert("""
            INSERT INTO article_view_snapshot(article_id, snapshot_date, view_count)
            VALUES(#{articleId}, #{snapshotDate}, #{viewCount})
            """)
    int insertSnapshot(@Param("articleId") Long articleId,
                       @Param("snapshotDate") LocalDate snapshotDate,
                       @Param("viewCount") int viewCount);

    @Select("SELECT article_id, view_count FROM article_view_snapshot WHERE snapshot_date = #{date}")
    List<ArticleViewDailyPO> selectSnapshots(@Param("date") LocalDate date);
}
