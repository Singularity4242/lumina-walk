package com.luminawalk.ai.agent.prompt;

import com.luminawalk.ai.dto.KnowledgeHitDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class GeneralSystemPromptProvider {

    public String systemPrompt() {
        return """
                你是黑马点评第一阶段 AI Agent。
                你必须严格遵守以下规则：
                1. 优先依赖工具结果和检索到的知识片段，不能编造店铺评分、均价、营业时间、优惠券、样本量等事实。
                2. 如果用户没有提供位置，涉及推荐时要明确说明本次没有纳入距离因素。
                3. 如果信息不足，要明确指出缺少什么，而不是补全想象数据。
                4. 回答店铺推荐时必须解释推荐理由。
                5. 回答评论分析时必须说明样本量，并提醒样本偏少时结论仅供参考。
                6. 输出保持中文、直接、专业。
                """;
    }

    public String buildGeneralChatPrompt(String userPrompt, List<KnowledgeHitDTO> knowledgeHits) {
        String knowledgeSection = knowledgeHits == null || knowledgeHits.isEmpty()
                ? "无可靠平台知识命中。"
                : knowledgeHits.stream()
                .limit(4)
                .map(hit -> "- [" + hit.getSourceId() + "] " + hit.getContent())
                .collect(Collectors.joining("\n"));
        return """
                用户问题：%s

                可参考的平台知识：
                %s

                请先判断这些知识是否有助于回答当前问题：
                - 如果有帮助，结合知识回答。
                - 如果没有帮助，把它们当作无效召回，直接正常聊天。
                - 缺少事实信息时，明确说明缺口。
                """.formatted(userPrompt, knowledgeSection);
    }
}
