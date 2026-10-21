package com.luminawalk.ai.agent.prompt;

import com.luminawalk.ai.dto.AgentRecommendItem;
import com.luminawalk.ai.dto.AgentRecommendResponse;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component

public class ShopRecommendPromptProvider {

    public String buildTextPrompt(String query, AgentRecommendResponse response) {
        String candidateText = response.getItems().stream()
                .map(this::formatItem)
                .collect(Collectors.joining("\n"));
        return """
                用户想找：%s
                额外提示：%s

                已筛选出的候选店铺如下，请只基于这些候选生成推荐答案，不要新增店铺：
                %s

                输出要求：
                1. 先给一句总体判断。
                2. 按顺序推荐店铺，并解释为什么推荐。
                3. 如果没有位置，要明确说明这次没有纳入距离因素。
                4. 如果结果为空，要明确说明信息不足或当前没有合适候选。
                """.formatted(query, blankToDefault(response.getMissingInfoNotice()), candidateText.isBlank() ? "暂无候选。" : candidateText);
    }

    public String buildSummaryPrompt(String query, AgentRecommendResponse response) {
        return buildTextPrompt(query, response) + "\n请额外输出一段简短总结。";
    }

    private String formatItem(AgentRecommendItem item) {
        return "- 店铺ID=%s, 名称=%s, 商圈=%s, 地址=%s, 人均=%s, 评分=%s, 评论数=%s, 销量=%s, 距离=%s, 预生成理由=%s"
                .formatted(item.getShopId(), item.getShopName(), item.getArea(), item.getAddress(), item.getAvgPrice(),
                        item.getRating(), item.getComments(), item.getSold(), item.getDistanceMeters(), item.getReason());
    }

    private String blankToDefault(String text) {
        return text == null || text.isBlank() ? "无" : text;
    }
}
