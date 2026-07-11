package cn.sutone.ai.test.domain.agent.service.ai_writing.markdown;

import cn.sutone.ai.domain.agent.service.ai_writing.markdown.MarkdownNormalizer;
import org.junit.jupiter.api.Test;

class MarkdownNormalizerDiagTest {

    @Test
    void diag() {
        String realJam = "##一、概述###1.1什么是 JVM内存结构###1.2为什么理解内存结构很重要##二、线程私有区域###2.1程序计数器###2.2 Java虚拟机栈####2.2.1栈帧结构# JVM内存结构全景解析##一、概述###1.1什么是 JVM内存结构**JVM内存结构**是指 Java虚拟机在运行过程中划分的内存区域。###1.2为什么理解内存结构很重要深入理解是必修课：- **性能调优**：合理配置- **故障排查**：定位根因- **面试高频**：必考点";
        System.out.println("=========== REAL JAM INPUT ===========");
        System.out.println(realJam);
        System.out.println("----------- NORMALIZED -----------");
        System.out.println(MarkdownNormalizer.normalize(realJam));
        System.out.println("==================================");
    }
}
