package com.luminawalk.ai.agent.scene;

public enum AiAgentScene {

    CHAT,
    SHOP_RECOMMEND,
    SHOP_INSIGHT;

    public static AiAgentScene from(String value) {
        if (value == null || value.isBlank()) {
            return CHAT;
        }
        for (AiAgentScene scene : values()) {
            if (scene.name().equalsIgnoreCase(value)) {
                return scene;
            }
        }
        return CHAT;
    }
}
