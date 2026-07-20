package cn.sutone.ai.test.domain.agent.service.memory;

import cn.sutone.ai.domain.agent.model.valobj.MemoryCandidate;
import cn.sutone.ai.domain.agent.model.valobj.MemoryTypeVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemoryCandidate / MemoryTypeVO / ScoredMemory 单元测试")
class MemoryTypeAndCandidateTest {

    @Nested
    @DisplayName("MemoryTypeVO")
    class MemoryTypeVOTests {

        @Test
        @DisplayName("fromCode 合法值返回正确枚举")
        void shouldParseValidCodes() {
            assertEquals(MemoryTypeVO.FACT, MemoryTypeVO.fromCode("fact"));
            assertEquals(MemoryTypeVO.PREFERENCE, MemoryTypeVO.fromCode("preference"));
            assertEquals(MemoryTypeVO.KNOWLEDGE, MemoryTypeVO.fromCode("knowledge"));
            assertEquals(MemoryTypeVO.EVENT, MemoryTypeVO.fromCode("event"));
        }

        @Test
        @DisplayName("isValid 正确判断合法/非法值")
        void shouldValidateCorrectly() {
            assertTrue(MemoryTypeVO.isValid("fact"));
            assertTrue(MemoryTypeVO.isValid("preference"));
            assertTrue(MemoryTypeVO.isValid("knowledge"));
            assertTrue(MemoryTypeVO.isValid("event"));
            assertFalse(MemoryTypeVO.isValid("invalid"));
            assertFalse(MemoryTypeVO.isValid(""));
            assertFalse(MemoryTypeVO.isValid(null));
        }

        @Test
        @DisplayName("fromCode 非法值返回默认 FACT")
        void shouldReturnFactForInvalidCode() {
            assertEquals(MemoryTypeVO.FACT, MemoryTypeVO.fromCode("nonexistent"));
        }
    }

    @Nested
    @DisplayName("MemoryCandidate")
    class MemoryCandidateTests {

        @Test
        @DisplayName("创建候选记忆，字段正确")
        void shouldCreateCandidate() {
            MemoryCandidate c = new MemoryCandidate("用户偏好Java", "preference", "user");

            assertEquals("用户偏好Java", c.content());
            assertEquals("preference", c.type());
            assertEquals("user", c.attributedTo());
        }

        @Test
        @DisplayName("attributedTo 为空时正常")
        void shouldAllowNullAttribution() {
            MemoryCandidate c = new MemoryCandidate("test", "fact", null);
            assertNull(c.attributedTo());
        }
    }
}
