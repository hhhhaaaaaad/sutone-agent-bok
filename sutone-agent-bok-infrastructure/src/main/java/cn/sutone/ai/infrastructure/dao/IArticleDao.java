package cn.sutone.ai.infrastructure.dao;

import cn.sutone.ai.infrastructure.dao.po.ArticlePO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文章 DAO
 */
@Mapper
public interface IArticleDao {

    @Select("SELECT IFNULL(MAX(id), 0) + 1 FROM article")
    Long nextArticleId();

    @Insert("""
            INSERT INTO article(id, draft_id, author_id, title, content_md, content_html, summary, cover_url, status, publish_time, is_deleted)
            VALUES(#{id}, #{draftId}, #{authorId}, #{title}, #{contentMd}, #{contentHtml}, #{summary}, #{coverUrl}, #{status}, #{publishTime}, #{isDeleted})
            """)
    int insert(ArticlePO articlePO);

    @Insert("""
            UPDATE article
            SET title = #{title},
                content_md = #{contentMd},
                summary = #{summary},
                cover_url = #{coverUrl},
                status = #{status}
            WHERE id = #{id}
            """)
    int update(ArticlePO articlePO);

    @Select("""
            SELECT id, draft_id, author_id, title, content_md, content_html, summary, cover_url, status, publish_time, is_deleted, create_time, update_time
            FROM article
            WHERE id = #{articleId} AND is_deleted = 0
            """)
    ArticlePO queryByArticleId(@Param("articleId") Long articleId);

    @Select("""
            SELECT id, draft_id, author_id, title, content_md, content_html, summary, cover_url, status, publish_time, is_deleted, create_time, update_time
            FROM article
            WHERE draft_id = #{draftId} AND is_deleted = 0
            LIMIT 1
            """)
    ArticlePO queryByDraftId(@Param("draftId") Long draftId);

    @Select("""
            SELECT id, draft_id, author_id, title, content_md, content_html, summary, cover_url, status, publish_time, is_deleted, create_time, update_time
            FROM article
            WHERE is_deleted = 0
            ORDER BY publish_time DESC
            LIMIT #{pageSize} OFFSET #{offset}
            """)
    List<ArticlePO> queryPage(@Param("offset") Integer offset, @Param("pageSize") Integer pageSize);

    @Select("""
            SELECT COUNT(1)
            FROM article
            WHERE is_deleted = 0
            """)
    Integer countPage();
}
