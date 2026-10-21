package com.luminawalk.ai.agent.loop;

import com.luminawalk.ai.agent.AiScenePlanner;
import com.luminawalk.ai.agent.ShopInsightPreparer;
import com.luminawalk.ai.agent.ShopRecommendationPreparer;
import com.luminawalk.ai.agent.scene.AiAgentScene;
import com.luminawalk.ai.agent.scene.PlannerDecision;
import com.luminawalk.ai.rag.retriever.AiRagRetriever;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
@RequiredArgsConstructor
public class AgentLoopExecutor {

    private final AiScenePlanner aiScenePlanner;

    private final ShopRecommendationPreparer shopRecommendationPreparer;

    private final ShopInsightPreparer shopInsightPreparer;

    private final AiRagRetriever aiRagRetriever;

    public AgentLoopResult execute(AgentLoopRequest request) {
        PlannerDecision decision = request.getSceneOverride() != null
                ? aiScenePlanner.planWithoutModel(request.getPrompt(), request.getX(), request.getY(), request.getSceneOverride())
                : aiScenePlanner.plan(request.getPrompt(), request.getX(), request.getY());

        AiAgentScene scene = AiAgentScene.from(decision.getScene());
        AgentLoopResult result = new AgentLoopResult();
        result.setScene(scene.name());
        result.setToolLogs(new ArrayList<>());

        switch (scene) {
            case SHOP_RECOMMEND -> result.setRecommendation(
                    shopRecommendationPreparer.prepare(request.getPrompt(), request.getX(), request.getY(), decision, request.getTopK(), result.getToolLogs())
            );
            case SHOP_INSIGHT -> result.setShopInsight(
                    shopInsightPreparer.prepare(request.getShopId(), request.getPrompt(), decision, result.getToolLogs())
            );
            default -> result.setKnowledgeHits(aiRagRetriever.searchKnowledge(request.getPrompt()));
        }
        return result;
    }
}
