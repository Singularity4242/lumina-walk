package com.luminawalk.ai.agent.loop;

import com.luminawalk.ai.dto.AgentRecommendResponse;
import lombok.Data;

import java.util.List;

@Data
public class AgentLoopResult {

    private String scene;

    private AgentLoopExecution agentLoopExecution;

    private List<ToolExecutionLog> toolLogs;
}
