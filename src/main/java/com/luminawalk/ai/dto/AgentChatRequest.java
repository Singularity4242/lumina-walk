package com.luminawalk.ai.dto;

import lombok.Data;

@Data
public class AgentChatRequest {

    private Long conversationId;

    private String prompt;

    private Double x;

    private Double y;
}
