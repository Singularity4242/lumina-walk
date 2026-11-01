package com.luminawalk.ai.agent.loop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminawalk.ai.agent.prompt.AgentLoopPromptProvider;
import com.luminawalk.ai.config.AiAgentProperties;
import com.luminawalk.ai.tool.AgentLoopControlTools;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualToolAgentLoopExecutorTest {

    @ParameterizedTest
    @CsvSource({
            "周末推荐拍人像的地点, true",
            "推荐拍摄风景的地方, true",
            "周末推荐摄影地点并核实开放时间, false",
            "周末推荐摄影地点并查询费用, false",
            "周末摄影器材券是否包含押金, false",
            "周末摄影地点实时天气, false",
            "这个地点适合周末拍照吗, false",
            "想吃火锅和烧烤, false"
    })
    void shouldOnlyNudgeSimplePhotographyRecommendations(String question, boolean expectNudge) {
        AgentLoopRequest request = new AgentLoopRequest();
        request.setPrompt(question);
        assertRecommendationNudge(request, expectNudge);
    }

    @Test
    void shouldNotNudgePlaceAnalysisEvenWithRecommendationKeywords() {
        AgentLoopRequest request = new AgentLoopRequest();
        request.setPrompt("周末推荐拍摄攻略");
        request.setShopId(42L);
        assertRecommendationNudge(request, false);
    }

    private void assertRecommendationNudge(AgentLoopRequest request, boolean expectNudge) {
        AgentLoopPromptProvider provider = new AgentLoopPromptProvider();
        AtomicInteger calls = new AtomicInteger();
        ChatModel model = prompt -> {
            if (calls.incrementAndGet() == 1) {
                assertEquals(provider.systemPrompt(), prompt.getInstructions().get(0).getText());
                return new ChatResponse(List.of(new Generation(new AssistantMessage("", java.util.Map.of(),
                        List.of(new AssistantMessage.ToolCall("lookup", "function", "echo", "{\"text\":\"摄影资料\"}"))))));
            }
            boolean hasNudge = prompt.getInstructions().stream()
                    .anyMatch(message -> provider.simpleRecommendationFinishPrompt(request).equals(message.getText()));
            assertEquals(expectNudge, hasNudge);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("当前资料不足，请补充所在城市。"))));
        };
        AgentLoopToolRegistry registry = new AgentLoopToolRegistry(Arrays.asList(
                ToolCallbacks.from(new TestLoopTools(), new AgentLoopControlTools())));
        ManualToolAgentLoopExecutor executor = new ManualToolAgentLoopExecutor(
                model, registry, provider, new AiAgentProperties(), new ObjectMapper());
        AgentLoopExecution result = executor.execute(request, new ArrayList<>());
        assertEquals(AgentLoopStatus.FINISHED, result.getStatus());
        assertEquals(2, calls.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"人像逆光如何曝光？", "周末去哪拍照？", "这张租赁券包含押金吗？"})
    void shouldAllowAdviceOrClarificationToTerminateWithoutBusinessTools(String question) {
        AgentLoopPromptProvider provider = new AgentLoopPromptProvider();
        AgentLoopRequest request = new AgentLoopRequest();
        request.setPrompt(question);
        ChatModel model = prompt -> {
            assertEquals(provider.systemPrompt(), prompt.getInstructions().get(0).getText());
            assertEquals(provider.buildUserPrompt(request), prompt.getInstructions().get(1).getText());
            return new ChatResponse(List.of(new Generation(new AssistantMessage("", java.util.Map.of(),
                    List.of(new AssistantMessage.ToolCall("finish", "function", "terminate",
                            "{\"answer\":\"请补充必要条件；资料未核实。\",\"reason\":\"工具无法补足\"}"))))));
        };
        AgentLoopToolRegistry registry = new AgentLoopToolRegistry(Arrays.asList(
                ToolCallbacks.from(new AgentLoopControlTools())));
        ManualToolAgentLoopExecutor executor = new ManualToolAgentLoopExecutor(
                model, registry, provider, new AiAgentProperties(), new ObjectMapper());
        AgentLoopExecution result = executor.execute(request, new ArrayList<>());
        assertEquals(AgentLoopStatus.FINISHED, result.getStatus());
        assertTrue(result.isTerminatedByTool());
        assertEquals(1, result.getTotalSteps());
    }

    @Test
    void shouldRetryWhenModelReturnsBlankResponse() {
        AtomicInteger callCount = new AtomicInteger();
        ChatModel chatModel = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                if (callCount.incrementAndGet() == 1) {
                    return new ChatResponse(List.of(new Generation(new AssistantMessage(""))));
                }
                return new ChatResponse(List.of(new Generation(new AssistantMessage(
                        "",
                        java.util.Map.of(),
                        List.of(new AssistantMessage.ToolCall("call-2", "function", "terminate", "{\"answer\":\"重试后成功\",\"reason\":\"补充约束后模型正常结束\"}"))
                ))));
            }
        };

        AgentLoopToolRegistry registry = new AgentLoopToolRegistry(Arrays.asList(
                ToolCallbacks.from(new TestLoopTools(), new AgentLoopControlTools())
        ));
        AiAgentProperties properties = new AiAgentProperties();
        properties.getMultiAgent().setMaxSteps(5);
        ManualToolAgentLoopExecutor executor = new ManualToolAgentLoopExecutor(
                chatModel,
                registry,
                new AgentLoopPromptProvider(),
                properties,
                new ObjectMapper()
        );

        AgentLoopRequest request = new AgentLoopRequest();
        request.setPrompt("帮我处理一个问题");
        AgentLoopExecution execution = executor.execute(request, new ArrayList<>());

        assertEquals(AgentLoopStatus.FINISHED, execution.getStatus());
        assertEquals("重试后成功", execution.getFinalAnswer());
        assertEquals(2, execution.getTotalSteps());
    }

    @Test
    void shouldFinishViaTerminateToolWithinLoop() {
        AtomicInteger callCount = new AtomicInteger();
        ChatModel chatModel = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                if (callCount.incrementAndGet() == 1) {
                    return new ChatResponse(List.of(new Generation(new AssistantMessage(
                            "",
                            java.util.Map.of(),
                            List.of(new AssistantMessage.ToolCall("call-1", "function", "echo", "{\"text\":\"hello\"}"))
                    ))));
                }
                return new ChatResponse(List.of(new Generation(new AssistantMessage(
                        "",
                        java.util.Map.of(),
                        List.of(new AssistantMessage.ToolCall("call-2", "function", "terminate", "{\"answer\":\"最终答案\",\"reason\":\"信息已经足够\"}"))
                ))));
            }
        };

        AgentLoopToolRegistry registry = new AgentLoopToolRegistry(Arrays.asList(
                ToolCallbacks.from(new TestLoopTools(), new AgentLoopControlTools())
        ));
        AiAgentProperties properties = new AiAgentProperties();
        properties.getMultiAgent().setMaxSteps(20);
        ManualToolAgentLoopExecutor executor = new ManualToolAgentLoopExecutor(
                chatModel,
                registry,
                new AgentLoopPromptProvider(),
                properties,
                new ObjectMapper()
        );

        AgentLoopRequest request = new AgentLoopRequest();
        request.setPrompt("帮我一步步处理");
        AgentLoopExecution execution = executor.execute(request, new ArrayList<>());

        assertEquals(AgentLoopStatus.FINISHED, execution.getStatus());
        assertTrue(execution.isTerminatedByTool());
        assertEquals("最终答案", execution.getFinalAnswer());
        assertEquals(2, execution.getTotalSteps());
        assertTrue(execution.getTraces().stream().anyMatch(trace -> trace.getStatus() == AgentLoopStatus.THINKING));
        assertTrue(execution.getTraces().stream().anyMatch(trace -> trace.getStatus() == AgentLoopStatus.EXECUTING));
        assertTrue(execution.getTraces().stream().anyMatch(trace -> trace.getStatus() == AgentLoopStatus.FINISHED));
    }

    @Test
    void shouldStopWhenExceedingMaxSteps() {
        ChatModel chatModel = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage(
                        "",
                        java.util.Map.of(),
                        List.of(new AssistantMessage.ToolCall("call-loop", "function", "echo", "{\"text\":\"loop\"}"))
                ))));
            }
        };

        AgentLoopToolRegistry registry = new AgentLoopToolRegistry(Arrays.asList(
                ToolCallbacks.from(new TestLoopTools(), new AgentLoopControlTools())
        ));
        AiAgentProperties properties = new AiAgentProperties();
        properties.getMultiAgent().setMaxSteps(3);
        ManualToolAgentLoopExecutor executor = new ManualToolAgentLoopExecutor(
                chatModel,
                registry,
                new AgentLoopPromptProvider(),
                properties,
                new ObjectMapper()
        );

        AgentLoopRequest request = new AgentLoopRequest();
        request.setPrompt("一直循环");
        AgentLoopExecution execution = executor.execute(request, new ArrayList<>());

        assertEquals(AgentLoopStatus.ERROR, execution.getStatus());
        assertFalse(execution.isTerminatedByTool());
        assertTrue(execution.getFinalAnswer().contains("3 轮内仍未完成"));
        assertEquals(3, execution.getTotalSteps());
    }

    static class TestLoopTools {

        @Tool(description = "回显输入文本")
        public String echo(@ToolParam(description = "输入文本") String text) {
            return "echo:" + text;
        }
    }
}
