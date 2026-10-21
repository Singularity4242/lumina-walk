package com.luminawalk.ai.agent;

import com.luminawalk.ai.agent.loop.PreparedShopInsight;
import com.luminawalk.ai.agent.loop.ToolExecutionLog;
import com.luminawalk.ai.agent.scene.PlannerDecision;
import com.luminawalk.ai.config.AiAgentProperties;
import com.luminawalk.ai.dto.ShopToolDTO;
import com.luminawalk.ai.rag.retriever.AiRagRetriever;
import com.luminawalk.ai.tool.DianPingAgentTools;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ShopInsightPreparer {

    private final DianPingAgentTools tools;

    private final AiRagRetriever aiRagRetriever;

    private final AiAgentProperties aiAgentProperties;

    public PreparedShopInsight prepare(Long explicitShopId,
                                       String userPrompt,
                                       PlannerDecision decision,
                                       List<ToolExecutionLog> toolLogs) {
        PreparedShopInsight insight = new PreparedShopInsight();
        Long shopId = explicitShopId != null ? explicitShopId : decision.getShopId();
        if (shopId == null && StringUtils.hasText(decision.getKeyword())) {
            List<ShopToolDTO> keywordCandidates = tools.searchShopsByKeyword(decision.getKeyword());
            toolLogs.add(new ToolExecutionLog("searchShopsByKeyword", keywordCandidates.stream().limit(3).toList()));
            if (!keywordCandidates.isEmpty()) {
                shopId = keywordCandidates.get(0).getShopId();
            }
        }
        if (shopId == null) {
            insight.setMissingInfoNotice("缺少店铺ID或明确店铺名称，无法进行单店评论分析。");
            return insight;
        }

        insight.setShopId(shopId);
        ShopToolDTO shop = tools.getShopDetail(shopId);
        toolLogs.add(new ToolExecutionLog("getShopDetail", shop));
        if (shop == null) {
            insight.setMissingInfoNotice("未找到对应店铺，无法进行单店评论分析。");
            return insight;
        }
        insight.setShop(shop);
        insight.setReviewSummary(tools.getShopReviewSummary(shopId));
        toolLogs.add(new ToolExecutionLog("getShopReviewSummary", insight.getReviewSummary()));
        String retrievalQuery = shop.getName() + " 菜品 服务 环境 评价";
        insight.setReviewHits(aiRagRetriever.searchBlogReviewsByShop(
                shopId,
                retrievalQuery,
                Math.max(aiAgentProperties.getInsight().getMaxVectorHits(), 1)
        ));
        return insight;
    }
}
