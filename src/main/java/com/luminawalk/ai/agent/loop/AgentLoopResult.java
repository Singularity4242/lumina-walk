package com.luminawalk.ai.agent.loop;

import com.luminawalk.ai.dto.AgentRecommendResponse;
import lombok.Data;

@Data
public class AgentLoopResult {

    private String scene;

    private AgentRecommendResponse recommendation;

    private PreparedShopInsight shopInsight;

    private java.util.List<com.luminawalk.ai.dto.KnowledgeHitDTO> knowledgeHits;

    private java.util.List<ToolExecutionLog> toolLogs;
}
