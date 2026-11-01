package com.luminawalk.ai.agent.prompt;

import com.luminawalk.ai.agent.loop.AgentLoopRequest;
import com.luminawalk.ai.agent.loop.ToolExecutionLog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLoopPromptProviderTest {

    private final AgentLoopPromptProvider provider = new AgentLoopPromptProvider();

    @Test
    void shouldDistinguishMissingContextFromActualValues() {
        AgentLoopRequest request = new AgentLoopRequest();
        request.setPrompt("周末去哪里拍人像？");
        String prompt = provider.buildUserPrompt(request);
        assertTrue(prompt.contains(request.getPrompt()));
        assertTrue(prompt.contains("x=未提供, y=未提供"));
        assertTrue(prompt.contains("当前地点ID：未提供"));
        assertFalse(prompt.contains("null"));

        request.setX(0.0);
        request.setY(30.2);
        request.setShopId(42L);
        request.setTopK(3);
        prompt = provider.buildUserPrompt(request);
        assertTrue(prompt.contains("x=0.0, y=30.2"));
        assertTrue(prompt.contains("当前地点ID：42"));
        assertTrue(prompt.contains("期望召回数量：3"));
    }

    @Test
    void shouldPreserveCandidateSourcesAndLimitationsForFinalFormatting() {
        AgentLoopRequest request = new AgentLoopRequest();
        request.setPrompt("分析这个地点");
        request.setShopId(42L);
        String candidate = "地点42：未取得关联笔记，不能总结拍摄经验。";
        String prompt = provider.buildFinalAnswerPrompt(request, candidate,
                List.of(new ToolExecutionLog("getShopReviewSummary", "shopId=42, sampleCount=0")));

        assertTrue(prompt.contains(candidate));
        assertTrue(prompt.contains("getShopReviewSummary：shopId=42, sampleCount=0"));
        assertTrue(prompt.contains("当前地点ID：\n42"));
        assertTrue(prompt.contains("候选答案不是额外的事实依据"));
        String emptyPrompt = provider.buildFinalAnswerPrompt(request, " ", List.of());
        assertTrue(emptyPrompt.contains("本次没有使用工具"));
        assertTrue(emptyPrompt.contains("尚未获得可用结论"));
    }

    @Test
    void shouldAlignExecutionFormattingAndRetryWithPhotographyBoundaries() {
        assertTrue(provider.systemPrompt().contains("一般摄影建议"));
        assertTrue(provider.systemPrompt().contains("不随意跨城推荐"));
        assertTrue(provider.systemPrompt().contains("没有具体券查询工具"));
        assertTrue(provider.finalAnswerSystemPrompt().contains("保留来源标识、澄清问题"));
        assertTrue(provider.finalAnswerSystemPrompt().contains("无关联笔记"));
        assertTrue(provider.finalAnswerSystemPrompt().contains("缺少具体券资料"));
        assertTrue(provider.blankResponseRetryPrompt().contains("澄清问题或限制说明"));
        assertFalse(provider.systemPrompt().contains("黑马点评"));
        assertFalse(provider.finalAnswerSystemPrompt().contains("美食助手"));
    }
}
