package com.luminawalk.ai.agent.loop;

import com.luminawalk.ai.tool.AgentLoopControlTools;
import com.luminawalk.ai.tool.DianPingAgentTools;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AgentLoopToolRegistry {
    private final List<ToolCallback> toolCallbacks;

    private final Map<String, ToolCallback> callbackMap;

    @org.springframework.beans.factory.annotation.Autowired
    public AgentLoopToolRegistry(DianPingAgentTools dianPingAgentTools, AgentLoopControlTools controlTools) {
        this(Arrays.asList(ToolCallbacks.from(dianPingAgentTools, controlTools)));
    }

    AgentLoopToolRegistry(List<ToolCallback> toolCallbacks) {
        this.toolCallbacks = List.copyOf(toolCallbacks);
        Map<String, ToolCallback> map = new LinkedHashMap<>();
        for (ToolCallback toolCallback : toolCallbacks) {
            map.put(toolCallback.getToolDefinition().name(), toolCallback);
        }
        this.callbackMap = Map.copyOf(map);
    }

    public List<ToolCallback> getToolCallbacks() {
        return toolCallbacks;
    }

    public ToolCallback getToolCallback(String toolName) {
        return callbackMap.get(toolName);
    }
}
