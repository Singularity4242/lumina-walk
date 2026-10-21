package com.luminawalk.ai.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AgentRecommendResponse {

    private String scene = "SHOP_RECOMMEND";

    private String query;

    private String summary;

    private String missingInfoNotice;

    private List<AgentRecommendItem> items = new ArrayList<>();
}
