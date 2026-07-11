package cn.sutone.ai.test.domain.agent.service.ai_writing.markdown;

import cn.sutone.ai.domain.agent.service.ai_writing.markdown.MarkdownBlockRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MarkdownBlockRenderer 单元测试")
class MarkdownBlockRendererTest {

    @Test
    @DisplayName("识别结构化块 JSON")
    void shouldRecognizeBlockLine() {
        assertTrue(MarkdownBlockRenderer.isBlockLine("{\"type\":\"md_heading\",\"level\":2,\"text\":\"a\"}"));
        assertTrue(MarkdownBlockRenderer.isBlockLine("  {\"type\":\"md_paragraph\",\"text\":\"a\"}  "));
        assertFalse(MarkdownBlockRenderer.isBlockLine("这是普通文本"));
        assertFalse(MarkdownBlockRenderer.isBlockLine("{\"type\":\"drawio_node\"}"));
        assertFalse(MarkdownBlockRenderer.isBlockLine("## 标题"));
        assertFalse(MarkdownBlockRenderer.isBlockLine(null));
    }

    @Test
    @DisplayName("渲染标题块")
    void shouldRenderHeading() {
        assertEquals("## 标题", MarkdownBlockRenderer.renderLine("{\"type\":\"md_heading\",\"level\":2,\"text\":\"标题\"}"));
        assertEquals("#### 深层", MarkdownBlockRenderer.renderLine("{\"type\":\"md_heading\",\"level\":4,\"text\":\"深层\"}"));
        // level 越界应被夹到 1-6
        assertEquals("###### 超深", MarkdownBlockRenderer.renderLine("{\"type\":\"md_heading\",\"level\":9,\"text\":\"超深\"}"));
    }

    @Test
    @DisplayName("渲染段落块")
    void shouldRenderParagraph() {
        assertEquals("这是 **粗体** 段落",
                MarkdownBlockRenderer.renderLine("{\"type\":\"md_paragraph\",\"text\":\"这是 **粗体** 段落\"}"));
    }

    @Test
    @DisplayName("渲染无序/有序列表")
    void shouldRenderList() {
        String ul = MarkdownBlockRenderer.renderLine("{\"type\":\"md_list\",\"ordered\":false,\"items\":[\"a\",\"b\"]}");
        assertEquals("- a\n- b", ul);
        String ol = MarkdownBlockRenderer.renderLine("{\"type\":\"md_list\",\"ordered\":true,\"items\":[\"x\",\"y\"]}");
        assertEquals("1. x\n2. y", ol);
    }

    @Test
    @DisplayName("渲染表格块：表头/分隔行/数据行且单元格两侧留空格")
    void shouldRenderTable() {
        String json = "{\"type\":\"md_table\",\"headers\":[\"区域\",\"异常\"],\"rows\":[[\"堆\",\"OOM\"]]}";
        String result = MarkdownBlockRenderer.renderLine(json);
        String[] lines = result.split("\n");
        assertEquals("| 区域 | 异常 |", lines[0]);
        assertEquals("| --- | --- |", lines[1]);
        assertEquals("| 堆 | OOM |", lines[2]);
    }

    @Test
    @DisplayName("表格列数不齐时补空单元格")
    void shouldPadTableCells() {
        String json = "{\"type\":\"md_table\",\"headers\":[\"a\",\"b\",\"c\"],\"rows\":[[\"1\"]]}";
        String result = MarkdownBlockRenderer.renderLine(json);
        String dataRow = result.split("\n")[2];
        assertEquals("| 1 |  |  |", dataRow);
    }

    @Test
    @DisplayName("渲染代码块含语言标注")
    void shouldRenderCode() {
        String json = "{\"type\":\"md_code\",\"lang\":\"java\",\"text\":\"int a = 1;\"}";
        assertEquals("```java\nint a = 1;\n```", MarkdownBlockRenderer.renderLine(json));
    }

    @Test
    @DisplayName("渲染引用块")
    void shouldRenderQuote() {
        assertEquals("> 核心结论", MarkdownBlockRenderer.renderLine("{\"type\":\"md_quote\",\"text\":\"核心结论\"}"));
    }

    @Test
    @DisplayName("md_divider/md_done 特殊块")
    void shouldRenderSpecialBlocks() {
        assertEquals("---", MarkdownBlockRenderer.renderLine("{\"type\":\"md_divider\"}"));
        assertEquals("", MarkdownBlockRenderer.renderLine("{\"type\":\"md_done\"}"));
    }

    @Test
    @DisplayName("未知/非块类型返回 null")
    void shouldReturnNullForUnknown() {
        assertNull(MarkdownBlockRenderer.renderLine("{\"type\":\"unknown\"}"));
        assertNull(MarkdownBlockRenderer.renderLine("普通文本"));
        assertNull(MarkdownBlockRenderer.renderLine(null));
    }
}
