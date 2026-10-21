package com.luminawalk.ai.agent.loop;

import com.luminawalk.ai.agent.scene.AiAgentScene;
import lombok.Data;

@Data
public class AgentLoopRequest {

    private String prompt;

    private Double x;

    private Double y;

    private Long shopId;

    private Integer topK;

    private AiAgentScene sceneOverride;
}
