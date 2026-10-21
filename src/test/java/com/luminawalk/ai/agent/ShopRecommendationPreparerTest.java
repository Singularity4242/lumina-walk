package com.luminawalk.ai.agent;

import com.luminawalk.ai.agent.loop.ToolExecutionLog;
import com.luminawalk.ai.agent.scene.PlannerDecision;
import com.luminawalk.ai.dto.AgentRecommendResponse;
import com.luminawalk.ai.dto.ShopToolDTO;
import com.luminawalk.ai.tool.DianPingAgentTools;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class ShopRecommendationPreparerTest {
    @Test
    void shouldRankAndExplainCandidates() {
        DianPingAgentTools tools = new DianPingAgentTools(null, null, null, null) {
            @Override
            public List<ShopToolDTO> vectorSearchShopProfiles(String query, Integer topK) {
                return List.of(
                        shop(1L, "A店", 4.9, 100L, 3000, 5000, 0.9),
                        shop(2L, "B店", 4.6, 280L, 5000, 9000, 0.8)
                );
            }

            @Override
            public List<ShopToolDTO> searchShopsByKeyword(String keyword) {
                return List.of();
            }
        };

        ShopRecommendationPreparer preparer = new ShopRecommendationPreparer(tools);
        PlannerDecision decision = new PlannerDecision();
        decision.setKeyword("火锅");
        decision.setBudgetMin(80);
        decision.setBudgetMax(140);

        AgentRecommendResponse response = preparer.prepare("人均100左右的火锅", null, null, decision, 5, new ArrayList<ToolExecutionLog>());

        assertEquals(1L, response.getItems().get(0).getShopId());
        assertTrue(response.getItems().get(0).getReason().contains("评分"));
        assertFalse(response.getMissingInfoNotice().isBlank());
    }

    private ShopToolDTO shop(Long id, String name, Double rating, Long avgPrice, Integer comments, Integer sold, Double semanticScore) {
        ShopToolDTO dto = new ShopToolDTO();
        dto.setShopId(id);
        dto.setName(name);
        dto.setRating(rating);
        dto.setAvgPrice(avgPrice);
        dto.setComments(comments);
        dto.setSold(sold);
        dto.setSemanticScore(semanticScore);
        return dto;
    }

}
