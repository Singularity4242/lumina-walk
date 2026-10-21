package com.luminawalk.ai.service.impl;

import com.luminawalk.ai.agent.loop.AgentLoopExecutor;
import com.luminawalk.ai.agent.loop.AgentLoopRequest;
import com.luminawalk.ai.agent.loop.AgentLoopResult;
import com.luminawalk.ai.agent.loop.AgentLoopExecution;
import com.luminawalk.ai.agent.loop.AgentLoopStatus;
import com.luminawalk.ai.agent.scene.AiAgentScene;
import com.luminawalk.ai.config.AiAgentProperties;
import com.luminawalk.ai.dto.AgentChatRequest;
import com.luminawalk.ai.dto.AgentRecommendRequest;
import com.luminawalk.ai.dto.AgentRecommendResponse;
import com.luminawalk.ai.dto.ConversationSummaryDTO;
import com.luminawalk.ai.dto.IndexRebuildResponse;
import com.luminawalk.ai.dto.MessageDTO;
import com.luminawalk.ai.dto.ShopInsightResponse;
import com.luminawalk.ai.memory.ConversationMemoryService;
import com.luminawalk.ai.rag.indexer.AiVectorIndexService;
import com.luminawalk.ai.service.AiAgentService;
import com.luminawalk.ai.service.ChatStreamSession;
import com.luminawalk.dto.UserDTO;
import com.luminawalk.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAgentServiceImpl implements AiAgentService {

    private final ConversationMemoryService conversationMemoryService;

    private final AgentLoopExecutor agentLoopExecutor;

    private final AiVectorIndexService aiVectorIndexService;

    private final AiAgentProperties aiAgentProperties;

    @Override
    public ChatStreamSession chat(AgentChatRequest request) {
        Long userId = currentUserId();
        if (!StringUtils.hasText(request.getPrompt())) {
            throw new IllegalArgumentException("prompt 不能为空");
        }

        var conversation = conversationMemoryService.getOrCreateConversation(userId, request.getConversationId(), request.getPrompt(), AiAgentScene.MULTI_AGENT.name());
        List<Message> historyMessages = conversationMemoryService.loadRecentMessages(conversation.getId(), aiAgentProperties.getMemory().getWindowSize());
        conversationMemoryService.appendUserMessage(conversation.getId(), request.getPrompt());

        AgentLoopRequest loopRequest = new AgentLoopRequest();
        loopRequest.setPrompt(request.getPrompt());
        loopRequest.setX(request.getX());
        loopRequest.setY(request.getY());
        loopRequest.setHistoryMessages(historyMessages);
        AgentLoopResult loopResult = agentLoopExecutor.execute(loopRequest);
        loopResult.getToolLogs().forEach(log -> conversationMemoryService.appendToolMessage(conversation.getId(), log.getToolName(), log.getPayload()));
        conversationMemoryService.updateConversation(conversation.getId(), loopResult.getScene(), request.getPrompt());

        Flux<String> stream = Flux.just(buildAgentLoopReply(loopResult.getAgentLoopExecution()))
                .transform(flux -> persistAssistantReply(conversation.getId(), request.getPrompt(), loopResult.getScene(), flux));
        return new ChatStreamSession(conversation.getId(), stream);
    }

    @Override
    public List<ConversationSummaryDTO> listConversations() {
        return conversationMemoryService.listConversations(currentUserId());
    }

    @Override
    public List<MessageDTO> listMessages(Long conversationId) {
        return conversationMemoryService.listMessages(currentUserId(), conversationId);
    }

    @Override
    public AgentRecommendResponse recommend(AgentRecommendRequest request) {
        AgentLoopRequest loopRequest = new AgentLoopRequest();
        loopRequest.setPrompt(request.getQuery());
        loopRequest.setX(request.getX());
        loopRequest.setY(request.getY());
        loopRequest.setTopK(request.getTopK());
        AgentLoopResult loopResult = agentLoopExecutor.execute(loopRequest);
        AgentRecommendResponse response = new AgentRecommendResponse();
        response.setScene(AiAgentScene.MULTI_AGENT.name());
        response.setQuery(request.getQuery());
        response.setSummary(buildAgentLoopReply(loopResult.getAgentLoopExecution()));
        if (request.getX() == null || request.getY() == null) {
            response.setMissingInfoNotice("未提供位置，本次推荐未纳入距离因素。");
        }
        return response;
    }

    @Override
    public ShopInsightResponse analyzeShop(Long shopId) {
        AgentLoopRequest loopRequest = new AgentLoopRequest();
        loopRequest.setPrompt("请分析店铺ID=" + shopId + " 的真实评价，重点关注招牌菜、环境和服务");
        loopRequest.setShopId(shopId);
        AgentLoopResult loopResult = agentLoopExecutor.execute(loopRequest);

        ShopInsightResponse response = new ShopInsightResponse();
        response.setShopId(shopId);
        response.setSummary(buildAgentLoopReply(loopResult.getAgentLoopExecution()));
        response.setSampleSize(0);
        response.setSampleSizeNotice("当前结果由 ReAct Agent 生成，未走固定样本分析链路。");
        return response;
    }

    @Override
    public IndexRebuildResponse rebuildAll() {
        return aiVectorIndexService.rebuildAll();
    }

    @Override
    public IndexRebuildResponse rebuildShop(Long shopId) {
        return aiVectorIndexService.rebuildShop(shopId);
    }


    private Flux<String> persistAssistantReply(Long conversationId, String prompt, String scene, Flux<String> flux) {
        StringBuilder buffer = new StringBuilder();
        return flux.doOnNext(buffer::append)
                .doOnComplete(() -> {
                    conversationMemoryService.appendAssistantMessage(conversationId, buffer.toString());
                    conversationMemoryService.updateConversation(conversationId, scene, prompt);
                });
    }

    private Long currentUserId() {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            throw new IllegalStateException("当前未登录");
        }
        return user.getId();
    }


    private String buildAgentLoopReply(AgentLoopExecution execution) {
        if (execution == null) {
            return "抱歉，当前多轮推理没有产生可用结果，请稍后重试。";
        }
        if (StringUtils.hasText(execution.getFinalAnswer())) {
            return execution.getFinalAnswer();
        }
        if (execution.getStatus() == AgentLoopStatus.ERROR && StringUtils.hasText(execution.getErrorMessage())) {
            return "抱歉，多轮推理过程中出现错误：" + execution.getErrorMessage();
        }

        return "抱歉，当前多轮推理没有产生可用结果，请稍后重试。";
    }
}