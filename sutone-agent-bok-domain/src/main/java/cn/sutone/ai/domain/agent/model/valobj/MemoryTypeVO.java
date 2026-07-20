package cn.sutone.ai.domain.agent.model.valobj;

import java.util.Arrays;

public enum MemoryTypeVO {

    FACT("fact"),
    PREFERENCE("preference"),
    KNOWLEDGE("knowledge"),
    EVENT("event");

    private final String code;

    MemoryTypeVO(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static boolean isValid(String type) {
        return Arrays.stream(values()).anyMatch(v -> v.code.equals(type));
    }

    public static MemoryTypeVO fromCode(String code) {
        return Arrays.stream(values())
                .filter(v -> v.code.equals(code))
                .findFirst()
                .orElse(FACT);
    }
}
