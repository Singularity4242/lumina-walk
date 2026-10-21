package com.luminawalk.ai.controller;

import com.luminawalk.ai.dto.AgentChatRequest;
import com.luminawalk.ai.dto.AgentRecommendRequest;
import com.luminawalk.ai.dto.AgentRecommendResponse;
import com.luminawalk.ai.dto.ConversationSummaryDTO;
import com.luminawalk.ai.dto.IndexRebuildResponse;
import com.luminawalk.ai.dto.MessageDTO;
import com.luminawalk.ai.dto.ShopInsightResponse;
import com.luminawalk.ai.service.AiAgentService;
import com.luminawalk.ai.service.ChatStreamSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/agent")
public class AiAgentController {

    private final AiAgentService aiAgentService;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<String>> chat(@RequestBody AgentChatRequest request) {
        ChatStreamSession session = aiAgentService.chat(request);
        return ResponseEntity.ok()
                .header("X-Conversation-Id", String.valueOf(session.getConversationId()))
                .body(session.getStream());
    }

    @GetMapping("/conversations")
    public List<ConversationSummaryDTO> conversations() {
        return aiAgentService.listConversations();
    }

    @GetMapping("/conversations/{id}/messages")
    public List<MessageDTO> messages(@PathVariable("id") Long conversationId) {
        return aiAgentService.listMessages(conversationId);
    }

    @PostMapping("/recommend")
    public AgentRecommendResponse recommend(@RequestBody AgentRecommendRequest request) {
        return aiAgentService.recommend(request);
    }

    @GetMapping("/analyze/shop/{shopId}")
    public ShopInsightResponse analyzeShop(@PathVariable("shopId") Long shopId) {
        return aiAgentService.analyzeShop(shopId);
    }

    @PostMapping("/index/rebuild/all")
    public IndexRebuildResponse rebuildAll() {
        return aiAgentService.rebuildAll();
    }

    @PostMapping("/index/rebuild/shop/{shopId}")
    public IndexRebuildResponse rebuildShop(@PathVariable("shopId") Long shopId) {
        return aiAgentService.rebuildShop(shopId);
    }
}
