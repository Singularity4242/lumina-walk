package com.luminawalk.ai.agent.loop;

import com.luminawalk.ai.dto.BlogVectorHitDTO;
import com.luminawalk.ai.dto.ShopReviewSummaryDTO;
import com.luminawalk.ai.dto.ShopToolDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PreparedShopInsight {
    private Long shopId;

    private ShopToolDTO shop;

    private ShopReviewSummaryDTO reviewSummary;

    private List<BlogVectorHitDTO> reviewHits = new ArrayList<>();

    private String missingInfoNotice;
}
