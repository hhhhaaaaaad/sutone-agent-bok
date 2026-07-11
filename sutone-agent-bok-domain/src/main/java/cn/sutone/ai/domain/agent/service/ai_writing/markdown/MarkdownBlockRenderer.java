package cn.sutone.ai.domain.agent.service.ai_writing.markdown;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Markdown 结构化块渲染器（层四：结构化输出 + 确定性渲染）。
 *
 * <p>让 LLM 按行输出结构化 JSON 块（{@code {"type":"md_heading",...}} 等），后端按 type 拼装成
 * 标准 Markdown。空行、标题层级、表格对齐、单元格空格全部由代码保证，把「格式正确性」从
 * 「依赖模型自觉」变成「代码保证」。这与 draw.io 的 {@code drawio_node} 按行输出、后端渲染同源。</p>
 *
 * <p>协议（一行一个 JSON）：</p>
 * <pre>
 * {"type":"md_heading","level":2,"text":"标题"}
 * {"type":"md_paragraph","text":"段落文本"}
 * {"type":"md_list","ordered":false,"items":["a","b"]}
 * {"type":"md_table","headers":["列1","列2"],"rows":[["a","b"]]}
 * {"type":"md_code","lang":"java","text":"代码"}
 * {"type":"md_quote","text":"引用"}
 * {"type":"md_divider"}
 * {"type":"md_done"}
 * </pre>
 */
public final class MarkdownBlockRenderer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 已知的 Markdown 块类型前缀 */
    private static final String TYPE_PREFIX = "md_";

    private MarkdownBlockRenderer() {
    }

    /**
     * 判断一行文本是否为可识别的 Markdown 结构化块 JSON。
     */
    public static boolean isBlockLine(String line) {
        if (null == line) {
            return false;
        }
        String trimmed = line.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return false;
        }
        try {
            JsonNode node = MAPPER.readTree(trimmed);
            String type = node.path("type").asText("");
            return type.startsWith(TYPE_PREFIX);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 把一行块 JSON 渲染成标准 Markdown 片段（不含前后空行，由调用方用 \n\n 拼接）。
     *
     * @param line 单行块 JSON
     * @return 渲染后的 Markdown 片段；无法识别时返回 null
     */
    public static String renderLine(String line) {
        if (null == line || line.isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(line.trim());
            String type = node.path("type").asText("");
            return switch (type) {
                case "md_heading" -> renderHeading(node);
                case "md_paragraph" -> node.path("text").asText("").strip();
                case "md_list" -> renderList(node);
                case "md_table" -> renderTable(node);
                case "md_code" -> renderCode(node);
                case "md_quote" -> renderQuote(node);
                case "md_divider" -> "---";
                case "md_done" -> "";
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    private static String renderHeading(JsonNode node) {
        int level = node.path("level").asInt(2);
        if (level < 1) {
            level = 1;
        }
        if (level > 6) {
            level = 6;
        }
        String text = node.path("text").asText("").strip();
        return "#".repeat(level) + " " + text;
    }

    private static String renderList(JsonNode node) {
        boolean ordered = node.path("ordered").asBoolean(false);
        JsonNode items = node.path("items");
        if (!items.isArray() || items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int idx = 1;
        for (JsonNode item : items) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            if (ordered) {
                sb.append(idx++).append(". ");
            } else {
                sb.append("- ");
            }
            sb.append(item.asText("").strip());
        }
        return sb.toString();
    }

    private static String renderTable(JsonNode node) {
        JsonNode headers = node.path("headers");
        JsonNode rows = node.path("rows");
        if (!headers.isArray() || headers.isEmpty()) {
            return "";
        }
        int cols = headers.size();
        StringBuilder sb = new StringBuilder();

        // 表头行
        sb.append("| ");
        List<String> headerCells = new ArrayList<>();
        for (JsonNode h : headers) {
            headerCells.add(escapeCell(h.asText("")));
        }
        sb.append(String.join(" | ", headerCells)).append(" |\n");

        // 分隔行
        sb.append("|");
        for (int i = 0; i < cols; i++) {
            sb.append(" --- |");
        }

        // 数据行
        if (rows.isArray()) {
            for (JsonNode row : rows) {
                sb.append("\n| ");
                List<String> cells = new ArrayList<>();
                for (int i = 0; i < cols; i++) {
                    String val = (row.isArray() && i < row.size()) ? row.get(i).asText("") : "";
                    cells.add(escapeCell(val));
                }
                sb.append(String.join(" | ", cells)).append(" |");
            }
        }
        return sb.toString();
    }

    private static String renderCode(JsonNode node) {
        String lang = node.path("lang").asText("").strip();
        String text = node.path("text").asText("");
        // 去掉尾部多余换行，交由拼接层控制
        return "```" + lang + "\n" + text.stripTrailing() + "\n```";
    }

    private static String renderQuote(JsonNode node) {
        String text = node.path("text").asText("").strip();
        StringBuilder sb = new StringBuilder();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append("> ").append(lines[i]);
        }
        return sb.toString();
    }

    /** 表格单元格内的 | 与换行会破坏表格结构，需转义/清理 */
    private static String escapeCell(String value) {
        if (null == value) {
            return "";
        }
        return value.replace("\n", " ").replace("|", "\\|").strip();
    }
}
