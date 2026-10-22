package com.luminawalk.ai.service;

import com.luminawalk.ai.dto.*;
import com.luminawalk.dto.CreateChatMessageRequest;
import com.luminawalk.dto.CreateChatMessageResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface AiAgentService {

    ConversationSummaryDTO createConversation();

    CreateChatMessageResponse createChatMessage(CreateChatMessageRequest request);

    SseEmitter streamConversation(Long conversationId);

    List<ConversationSummaryDTO> listConversations();

    List<MessageDTO> listMessages(Long conversationId);

    AgentRecommendResponse recommend(AgentRecommendRequest request);

    ShopInsightResponse analyzeShop(Long shopId);

    IndexRebuildResponse rebuildAll();

    IndexRebuildResponse rebuildShop(Long shopId);
}
