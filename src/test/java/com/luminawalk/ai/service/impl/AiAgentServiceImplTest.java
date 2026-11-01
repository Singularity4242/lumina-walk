package com.luminawalk.ai.service.impl;

import com.luminawalk.ai.agent.loop.AgentLoopExecutor;
import com.luminawalk.ai.agent.loop.AgentLoopExecution;
import com.luminawalk.ai.agent.loop.AgentLoopRequest;
import com.luminawalk.ai.agent.loop.AgentLoopResult;
import com.luminawalk.ai.config.AiAgentProperties;
import com.luminawalk.ai.entity.AiConversation;
import com.luminawalk.ai.entity.AiMessage;
import com.luminawalk.ai.memory.ConversationMemoryService;
import com.luminawalk.ai.rag.indexer.AiVectorIndexService;
import com.luminawalk.dto.CreateChatMessageRequest;
import com.luminawalk.dto.CreateChatMessageResponse;
import com.luminawalk.dto.UserDTO;
import com.luminawalk.event.ChatMessageCreatedEvent;
import com.luminawalk.service.ChatSseService;
import com.luminawalk.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiAgentServiceImplTest {

    @Test
    void analyzeShopShouldKeepPlaceIdAndRequestEvidenceBasedPhotographyAnalysis() {
        AgentLoopExecutor executor = mock(AgentLoopExecutor.class);
        AgentLoopExecution execution = new AgentLoopExecution();
        execution.setFinalAnswer("该地点暂无关联摄影笔记。");
        AgentLoopResult result = new AgentLoopResult();
        result.setAgentLoopExecution(execution);
        when(executor.execute(any(AgentLoopRequest.class))).thenReturn(result);
        AiAgentServiceImpl service = new AiAgentServiceImpl(
                mock(ConversationMemoryService.class), executor, mock(AiVectorIndexService.class),
                new AiAgentProperties(), mock(ApplicationEventPublisher.class), mock(ChatSseService.class));

        assertEquals(execution.getFinalAnswer(), service.analyzeShop(42L).getSummary());
        ArgumentCaptor<AgentLoopRequest> captor = ArgumentCaptor.forClass(AgentLoopRequest.class);
        verify(executor).execute(captor.capture());
        assertEquals(42L, captor.getValue().getShopId());
        String prompt = captor.getValue().getPrompt();
        assertTrue(prompt.contains("地点ID=42"));
        assertTrue(prompt.contains("关联摄影笔记"));
        assertTrue(prompt.contains("无关联笔记时只总结地点资料"));
        assertFalse(prompt.contains("招牌菜"));
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void createChatMessageShouldPersistUserMessageAndPublishEvent() {
        ConversationMemoryService conversationMemoryService = mock(ConversationMemoryService.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        AiAgentServiceImpl service = new AiAgentServiceImpl(
                conversationMemoryService,
                mock(AgentLoopExecutor.class),
                mock(AiVectorIndexService.class),
                new AiAgentProperties(),
                publisher,
                mock(ChatSseService.class)
        );

        UserDTO user = new UserDTO();
        user.setId(1L);
        UserHolder.saveUser(user);

        AiConversation conversation = new AiConversation().setId(100L).setUserId(1L);
        AiMessage message = new AiMessage().setId(200L).setConversationId(100L).setContent("你好");
        when(conversationMemoryService.getOrCreateConversation(1L, null, "你好", "MULTI_AGENT"))
                .thenReturn(conversation);
        when(conversationMemoryService.appendUserMessage(100L, "你好")).thenReturn(message);

        CreateChatMessageRequest request = new CreateChatMessageRequest();
        request.setContent("你好");
        request.setX(120.1);
        request.setY(30.2);

        CreateChatMessageResponse response = service.createChatMessage(request);

        assertEquals(100L, response.getConversationId());
        assertEquals(200L, response.getChatMessageId());
        verify(conversationMemoryService).appendUserMessage(100L, "你好");

        ArgumentCaptor<ChatMessageCreatedEvent> captor = ArgumentCaptor.forClass(ChatMessageCreatedEvent.class);
        verify(publisher).publishEvent(captor.capture());
        ChatMessageCreatedEvent event = captor.getValue();
        assertEquals(1L, event.getUserId());
        assertEquals(100L, event.getConversationId());
        assertEquals(200L, event.getChatMessageId());
        assertEquals("你好", event.getContent());
        assertEquals(120.1, event.getX());
        assertEquals(30.2, event.getY());
    }
}
