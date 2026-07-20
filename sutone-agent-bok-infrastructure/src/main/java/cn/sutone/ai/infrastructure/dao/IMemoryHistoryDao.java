package cn.sutone.ai.infrastructure.dao;

import cn.sutone.ai.infrastructure.dao.po.MemoryHistoryPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IMemoryHistoryDao {

    @Insert("""
            INSERT INTO memory_history(memory_id, session_id, old_content, new_content, event, role)
            VALUES(#{memoryId}, #{sessionId}, #{oldContent}, #{newContent}, #{event}, #{role})
            """)
    int insert(MemoryHistoryPO po);
}
