package com.luminawalk.ai.service.impl;

import com.luminawalk.ai.agent.loop.AgentLoopExecutor;
import com.luminawalk.ai.agent.loop.AgentLoopRequest;
import com.luminawalk.ai.agent.loop.AgentLoopResult;
import com.luminawalk.ai.agent.loop.PreparedShopInsight;
import com.luminawalk.ai.agent.prompt.GeneralSystemPromptProvider;
import com.luminawalk.ai.agent.prompt.ShopInsightPromptProvider;
import com.luminawalk.ai.agent.prompt.ShopRecommendPromptProvider;
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
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAgentServiceImpl implements AiAgentService {

    private final ChatClient aiChatClient;

    private final ConversationMemoryService conversationMemoryService;

    private final AgentLoopExecutor agentLoopExecutor;

    private final GeneralSystemPromptProvider generalSystemPromptProvider;

    private final ShopRecommendPromptProvider shopRecommendPromptProvider;

    private final ShopInsightPromptProvider shopInsightPromptProvider;

    private final AiVectorIndexService aiVectorIndexService;

    private final AiAgentProperties aiAgentProperties;

    @Override
    public ChatStreamSession chat(AgentChatRequest request) {
        Long userId = currentUserId();
        if (!StringUtils.hasText(request.getPrompt())) {
            throw new IllegalArgumentException("prompt 不能为空");
        }

        var conversation = conversationMemoryService.getOrCreateConversation(userId, request.getConversationId(), request.getPrompt(), AiAgentScene.CHAT.name());
        List<Message> historyMessages = conversationMemoryService.loadRecentMessages(conversation.getId(), aiAgentProperties.getMemory().getWindowSize());
        conversationMemoryService.appendUserMessage(conversation.getId(), request.getPrompt());

        AgentLoopRequest loopRequest = new AgentLoopRequest();
        loopRequest.setPrompt(request.getPrompt());
        loopRequest.setX(request.getX());
        loopRequest.setY(request.getY());
        AgentLoopResult loopResult = agentLoopExecutor.execute(loopRequest);
        loopResult.getToolLogs().forEach(log -> conversationMemoryService.appendToolMessage(conversation.getId(), log.getToolName(), log.getPayload()));
        conversationMemoryService.updateConversation(conversation.getId(), loopResult.getScene(), request.getPrompt());

        Flux<String> stream = buildStream(historyMessages, request.getPrompt(), loopResult)
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
        loopRequest.setSceneOverride(AiAgentScene.SHOP_RECOMMEND);
        AgentLoopResult loopResult = agentLoopExecutor.execute(loopRequest);
        AgentRecommendResponse response = loopResult.getRecommendation();
        if (response.getSummary() == null) {
            response.setSummary(buildRecommendSummary(response));
        }
        return response;
    }

    @Override
    public ShopInsightResponse analyzeShop(Long shopId) {
        AgentLoopRequest loopRequest = new AgentLoopRequest();
        loopRequest.setPrompt("请分析这家店的真实评价，重点关注招牌菜、环境和服务");
        loopRequest.setShopId(shopId);
        loopRequest.setSceneOverride(AiAgentScene.SHOP_INSIGHT);
        AgentLoopResult loopResult = agentLoopExecutor.execute(loopRequest);
        PreparedShopInsight insight = loopResult.getShopInsight();
        if (!StringUtils.hasText(insight.getMissingInfoNotice()) && insight.getShop() != null) {
            BeanOutputConverter<ShopInsightResponse> converter = new BeanOutputConverter<>(ShopInsightResponse.class);
            try {
                ShopInsightResponse response = aiChatClient.prompt()
                        .system(generalSystemPromptProvider.systemPrompt())
                        .user(shopInsightPromptProvider.buildStructuredPrompt(loopRequest.getPrompt(), insight, converter.getFormat()))
                        .call()
                        .entity(converter);
                response.setShopId(insight.getShopId());
                response.setShopName(insight.getShop().getName());
                response.setSampleSize(insight.getReviewSummary() == null ? 0 : insight.getReviewSummary().getSampleCount());
                response.setSampleSizeNotice(insight.getReviewSummary() == null ? "当前没有可用评论样本。" : insight.getReviewSummary().getSampleSizeNotice());
                return response;
            }
            catch (ResourceAccessException ex) {
                log.warn("AI shop insight request timed out for shopId={}", shopId, ex);
                return fallbackInsightResponse(insight, "模型服务请求超时，以下结果为基于样本的降级摘要。");
            }
            catch (Exception ex) {
                log.warn("AI shop insight request failed for shopId={}", shopId, ex);
                return fallbackInsightResponse(insight, "模型服务暂时不可用，以下结果为基于样本的降级摘要。");
            }
        }

        ShopInsightResponse response = new ShopInsightResponse();
        response.setShopId(shopId);
        response.setSummary(insight.getMissingInfoNotice());
        response.setSampleSize(0);
        response.setSampleSizeNotice("当前没有可用评论样本。");
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

    private Flux<String> buildStream(List<Message> historyMessages, String rawPrompt, AgentLoopResult loopResult) {
        if (AiAgentScene.SHOP_RECOMMEND.name().equals(loopResult.getScene())) {
            if (loopResult.getRecommendation().getItems().isEmpty()) {
                return Flux.just(loopResult.getRecommendation().getSummary());
            }
            return streamAnswer(historyMessages, shopRecommendPromptProvider.buildTextPrompt(rawPrompt, loopResult.getRecommendation()));
        }
        if (AiAgentScene.SHOP_INSIGHT.name().equals(loopResult.getScene())) {
            PreparedShopInsight insight = loopResult.getShopInsight();
            if (StringUtils.hasText(insight.getMissingInfoNotice()) || insight.getShop() == null) {
                return Flux.just(insight.getMissingInfoNotice());
            }
            return streamAnswer(historyMessages, shopInsightPromptProvider.buildTextPrompt(rawPrompt, insight));
        }
        return streamAnswer(historyMessages, generalSystemPromptProvider.buildGeneralChatPrompt(rawPrompt, loopResult.getKnowledgeHits()));
    }

    private Flux<String> streamAnswer(List<Message> historyMessages, String prompt) {
        List<Message> messages = new ArrayList<>(historyMessages.size() + 2);
        messages.add(new SystemMessage(generalSystemPromptProvider.systemPrompt()));
        messages.addAll(historyMessages);
        messages.add(new UserMessage(prompt));
        return aiChatClient.prompt().messages(messages).stream().content();
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

    private String buildRecommendSummary(AgentRecommendResponse response) {
        if (response.getItems().isEmpty()) {
            return "当前没有足够候选店铺，请补充预算、位置或更明确的需求。";
        }
        var topItem = response.getItems().get(0);
        String locationNotice = StringUtils.hasText(response.getMissingInfoNotice()) ? response.getMissingInfoNotice() + " " : "";
        return locationNotice + "本次优先推荐 " + topItem.getShopName() + "，因为它在当前候选里综合语义匹配、评分、评论量和销量表现更好。";
    }

    private ShopInsightResponse fallbackInsightResponse(PreparedShopInsight insight, String prefix) {
        ShopInsightResponse response = new ShopInsightResponse();
        response.setShopId(insight.getShopId());
        response.setShopName(insight.getShop() == null ? null : insight.getShop().getName());
        response.setSampleSize(insight.getReviewSummary() == null ? 0 : insight.getReviewSummary().getSampleCount());
        response.setSampleSizeNotice(insight.getReviewSummary() == null ? "当前没有可用评论样本。" : insight.getReviewSummary().getSampleSizeNotice());
        response.setSummary(prefix + (insight.getReviewSummary() == null || !StringUtils.hasText(insight.getReviewSummary().getCombinedText())
                ? " 当前没有足够评论样本。"
                : " 当前共采样 " + insight.getReviewSummary().getSampleCount() + " 条探店笔记。"));
        response.setEnvironmentSummary(extractEnvironmentSummary(insight));
        response.setServiceSummary(extractServiceSummary(insight));
        response.setSuitableCrowd(extractSuitableCrowd(insight));
        response.getPositiveTags().addAll(extractPositiveTags(insight));
        response.getNegativeTags().addAll(extractNegativeTags(insight));
        response.getSignatureDishes().addAll(extractSignatureDishes(insight));
        response.getRiskTips().add("当前结果为降级摘要，未经过模型深度总结。");
        response.getRiskTips().addAll(extractNegativeTags(insight));
        return response;
    }

    private String extractEnvironmentSummary(PreparedShopInsight insight) {
        String text = combinedInsightText(insight);
        if (containsAny(text, "干净", "整洁", "卫生", "明亮")) {
            return "样本中多次提到门店整体较干净、整洁，环境基础体验稳定。";
        }
        if (containsAny(text, "吵", "拥挤", "热闹")) {
            return "样本显示高峰期可能偏吵或偏拥挤，环境舒适度受时段影响。";
        }
        return "当前样本中环境信息有限，建议结合非高峰时段再观察。";
    }

    private String extractServiceSummary(PreparedShopInsight insight) {
        String text = combinedInsightText(insight);
        if (containsAny(text, "服务", "及时", "热情", "周到", "加汤", "换骨碟")) {
            return "样本普遍认可服务响应速度和细节照顾，服务仍是主要优势。";
        }
        if (containsAny(text, "慢", "补给", "等位", "排队")) {
            return "服务整体不差，但高峰期会出现等位久或补给节奏变慢的问题。";
        }
        return "当前样本中服务信息有限，建议后续补充更多评论样本。";
    }

    private String extractSuitableCrowd(PreparedShopInsight insight) {
        String text = combinedInsightText(insight);
        if (containsAny(text, "聚餐", "团建", "生日", "朋友")) {
            return "更适合朋友聚餐、生日局和多人团建。";
        }
        if (containsAny(text, "家人", "长辈")) {
            return "也适合带家人或长辈来吃，尤其是不太能吃辣的人群。";
        }
        if (containsAny(text, "情侣", "约会")) {
            return "适合作为稳妥型约会餐厅，但不属于非常安静的氛围店。";
        }
        return "当前样本更偏向稳妥型聚餐场景。";
    }

    private List<String> extractPositiveTags(PreparedShopInsight insight) {
        List<String> tags = new ArrayList<>();
        String text = combinedInsightText(insight);
        addIfMatch(tags, text, "服务及时", "及时", "热情", "周到", "响应快");
        addIfMatch(tags, text, "环境干净", "干净", "整洁", "卫生", "明亮");
        addIfMatch(tags, text, "适合聚餐", "聚餐", "团建", "生日", "朋友");
        addIfMatch(tags, text, "食材新鲜", "新鲜", "稳定", "不会踩雷");
        return tags;
    }

    private List<String> extractNegativeTags(PreparedShopInsight insight) {
        List<String> tags = new ArrayList<>();
        String text = combinedInsightText(insight);
        addIfMatch(tags, text, "高峰期排队久", "排队", "等位");
        addIfMatch(tags, text, "价格偏高", "贵", "偏高", "超预算", "不便宜");
        addIfMatch(tags, text, "高峰期偏吵", "吵", "热闹", "拥挤");
        addIfMatch(tags, text, "停车不便", "停车");
        addIfMatch(tags, text, "补给节奏慢", "补给", "水果", "甜品");
        return tags;
    }

    private List<String> extractSignatureDishes(PreparedShopInsight insight) {
        List<String> dishes = new ArrayList<>();
        String text = combinedInsightText(insight);
        addIfMatch(dishes, text, "番茄锅", "番茄锅");
        addIfMatch(dishes, text, "虾滑", "虾滑");
        addIfMatch(dishes, text, "毛肚", "毛肚");
        addIfMatch(dishes, text, "肥牛", "肥牛");
        addIfMatch(dishes, text, "捞派豆花", "豆花");
        return dishes;
    }

    private String combinedInsightText(PreparedShopInsight insight) {
        if (insight.getReviewSummary() == null || !StringUtils.hasText(insight.getReviewSummary().getCombinedText())) {
            return "";
        }
        return insight.getReviewSummary().getCombinedText();
    }

    private void addIfMatch(List<String> target, String text, String tag, String... keywords) {
        if (target.contains(tag)) {
            return;
        }
        if (containsAny(text, keywords)) {
            target.add(tag);
        }
    }

    private boolean containsAny(String text, String... keywords) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}