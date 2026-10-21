package com.luminawalk.ai.agent.prompt;

import com.luminawalk.ai.agent.loop.AgentLoopRequest;
import org.springframework.stereotype.Component;

@Component
public class AgentLoopPromptProvider {

    public String systemPrompt() {
        return """
                你是黑马点评的多步 Agent。
                你必须遵守以下规则：
                1. 这是一个手动控制的 Think-Execute 循环，工具不会自动执行。
                2. 需要外部事实时优先调用工具，不要编造店铺、价格、评论、距离、规则等信息。
                3. 每一步尽量只做当前最必要的动作，避免重复调用相同工具。
                4. 如果已有足够信息完成任务，优先调用 terminate 工具明确结束任务。
                5. 如果工具结果不足，可以继续调用工具；如果用户问题本身就能直接回答，也可以直接输出最终回答。
                6. 严禁输出空内容。每一轮必须执行以下之一：
                   - 返回一个或多个工具调用
                   - 直接给出可发送给用户的最终答案
                   - 调用 terminate(answer, reason) 结束任务
                7. 输出保持中文、直接、专业。
                """;
    }

    public String buildUserPrompt(AgentLoopRequest request) {
        return """
                用户当前问题：%s
                用户位置：x=%s, y=%s
                目标店铺ID：%s
                候选数量上限：%s
                附加说明：
                - 如果需要推荐附近店铺，可以结合用户位置。
                - 如果用户指定了店铺ID，优先围绕该店铺完成任务。
                - 如果用户给了候选数量上限，优先按该上限控制候选范围。
                - 如果任务已经完成，请调用 terminate(answer, reason) 收尾。
                - 不允许返回空内容。
                """.formatted(request.getPrompt(), request.getX(), request.getY(), request.getShopId(), request.getTopK());
    }

    public String blankResponseRetryPrompt() {
        return "你上一轮没有返回任何可用内容。当前这一轮必须二选一：1. 调用至少一个工具；2. 直接输出最终答案；3. 调用 terminate(answer, reason) 结束。禁止空回复。";
    }
}
