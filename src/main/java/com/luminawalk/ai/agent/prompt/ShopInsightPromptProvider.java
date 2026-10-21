package com.luminawalk.ai.agent.prompt;

import com.luminawalk.ai.agent.loop.PreparedShopInsight;
import com.luminawalk.ai.config.AiAgentProperties;
import com.luminawalk.ai.dto.BlogVectorHitDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ShopInsightPromptProvider {

    private final AiAgentProperties aiAgentProperties;

    public String buildTextPrompt(String userPrompt, PreparedShopInsight insight) {
        return basePrompt(userPrompt, insight, null);
    }

    public String buildStructuredPrompt(String userPrompt, PreparedShopInsight insight, String format) {
        return basePrompt(userPrompt, insight, format);
    }

    private String basePrompt(String userPrompt, PreparedShopInsight insight, String format) {
        String reviewHits = insight.getReviewHits().stream()
                .limit(Math.max(aiAgentProperties.getInsight().getMaxVectorHits(), 1))
                .map(this::formatHit)
                .collect(Collectors.joining("\n"));
        return """
                用户问题：%s
                店铺信息：%s
                样本量说明：%s
                评论原文样本：
                %s

                向量召回的评论片段：
                %s

                输出要求：
                1. 必须体现样本量。
                2. 只能根据提供的信息总结，不要编造评分、营业时间、优惠券、特色菜。
                3. 如果样本少，请在结论中明确提醒。
                4. 要总结优点、缺点、招牌菜、环境、服务、适合人群和风险提示。
                %s
                """.formatted(
                userPrompt,
                insight.getShop(),
                insight.getReviewSummary() == null ? "暂无样本" : insight.getReviewSummary().getSampleSizeNotice(),
                insight.getReviewSummary() == null ? "暂无评论样本" : insight.getReviewSummary().getCombinedText(),
                reviewHits.isBlank() ? "暂无额外向量样本" : reviewHits,
                format == null ? "" : "输出格式要求：\n" + format
        );
    }

    private String formatHit(BlogVectorHitDTO hit) {
        return "- blogId=%s, score=%s, title=%s, content=%s"
                .formatted(hit.getBlogId(), hit.getScore(), hit.getTitle(), truncate(hit.getContent()));
    }

    private String truncate(String text) {
        int maxLength = Math.max(aiAgentProperties.getInsight().getMaxVectorHitChars(), 1);
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
