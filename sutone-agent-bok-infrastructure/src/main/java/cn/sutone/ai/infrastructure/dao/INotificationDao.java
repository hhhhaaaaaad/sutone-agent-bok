package cn.sutone.ai.infrastructure.dao;

import cn.sutone.ai.infrastructure.dao.po.NotificationPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface INotificationDao {

    @Insert("""
            INSERT INTO notification(user_id, type, sender_id, ref_id, content)
            VALUES(#{userId}, #{type}, #{senderId}, #{refId}, #{content})
            """)
    int insert(NotificationPO po);

    @Select("""
            SELECT id, user_id, type, sender_id, ref_id, content, is_read, create_time
            FROM notification
            WHERE user_id = #{userId}
            ORDER BY create_time DESC
            LIMIT #{offset}, #{pageSize}
            """)
    List<NotificationPO> queryByUserId(@Param("userId") Long userId,
                                       @Param("offset") int offset,
                                       @Param("pageSize") int pageSize);

    @Select("SELECT COUNT(1) FROM notification WHERE user_id = #{userId}")
    int countByUserId(@Param("userId") Long userId);

    @Select("SELECT COUNT(1) FROM notification WHERE user_id = #{userId} AND is_read = 0")
    int countUnread(@Param("userId") Long userId);

    @Update("UPDATE notification SET is_read = 1 WHERE id = #{id} AND user_id = #{userId}")
    int markRead(@Param("id") Long id, @Param("userId") Long userId);

    @Update("UPDATE notification SET is_read = 1 WHERE user_id = #{userId} AND is_read = 0")
    int markAllRead(@Param("userId") Long userId);
}
