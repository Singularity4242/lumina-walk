package com.luminawalk.ai.agent.scene;

import lombok.Data;

@Data
public class PlannerDecision {

    private String scene;

    private String reason;

    private Long shopId;

    private String keyword;

    private Integer typeId;

    private Integer budgetMin;

    private Integer budgetMax;

    private Boolean needNearby;

    private String informationGap;
}
