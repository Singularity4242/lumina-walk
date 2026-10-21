package com.luminawalk.ai.memory;

import com.luminawalk.ai.dto.ConversationSummaryDTO;
import com.luminawalk.ai.dto.MessageDTO;
import com.luminawalk.ai.entity.AiConversation;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

public interface ConversationMemoryService {

    AiConversation getOrCreateConversation(Long userId, Long conversationId, String prompt, String scene);

    void updateConversation(Long conversationId, String scene, String latestPrompt);

    void appendUserMessage(Long conversationId, String content);

    void appendAssistantMessage(Long conversationId, String content);

    void appendToolMessage(Long conversationId, String toolName, Object payload);

    List<Message> loadRecentMessages(Long conversationId, int windowSize);

    List<ConversationSummaryDTO> listConversations(Long userId);

    List<MessageDTO> listMessages(Long userId, Long conversationId);
}
