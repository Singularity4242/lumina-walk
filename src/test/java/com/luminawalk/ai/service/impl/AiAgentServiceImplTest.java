package com.luminawalk.ai.service.impl;

import com.luminawalk.ai.agent.loop.AgentLoopExecutor;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiAgentServiceImplTest {

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
