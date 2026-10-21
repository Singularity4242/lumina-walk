package com.luminawalk.ai.agent.loop;

import com.luminawalk.ai.agent.scene.AiAgentScene;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
@RequiredArgsConstructor
public class AgentLoopExecutor {

    private final ManualToolAgentLoopExecutor manualToolAgentLoopExecutor;

    public AgentLoopResult execute(AgentLoopRequest request) {

        AgentLoopResult result = new AgentLoopResult();
        result.setScene(AiAgentScene.MULTI_AGENT.name());
        result.setToolLogs(new ArrayList<>());

        result.setAgentLoopExecution(manualToolAgentLoopExecutor.execute(request, result.getToolLogs()));
        return result;
    }
}
