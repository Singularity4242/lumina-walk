package com.luminawalk.ai.service;

import com.luminawalk.ai.dto.*;

import java.util.List;

public interface AiAgentService {

    ChatStreamSession chat(AgentChatRequest request);

    List<ConversationSummaryDTO> listConversations();

    List<MessageDTO> listMessages(Long conversationId);

    AgentRecommendResponse recommend(AgentRecommendRequest request);

    ShopInsightResponse analyzeShop(Long shopId);

    IndexRebuildResponse rebuildAll();

    IndexRebuildResponse rebuildShop(Long shopId);
}
