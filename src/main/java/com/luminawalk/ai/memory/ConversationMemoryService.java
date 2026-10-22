package com.luminawalk.ai.memory;

import com.luminawalk.ai.dto.ConversationSummaryDTO;
import com.luminawalk.ai.dto.MessageDTO;
import com.luminawalk.ai.entity.AiConversation;
import com.luminawalk.ai.entity.AiMessage;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

public interface ConversationMemoryService {

    AiConversation getOrCreateConversation(Long userId, Long conversationId, String prompt, String scene);

    void updateConversation(Long conversationId, String scene, String latestPrompt);

    AiMessage appendUserMessage(Long conversationId, String content);

    AiMessage appendAssistantMessage(Long conversationId, String content);

    AiMessage appendToolMessage(Long conversationId, String toolName, Object payload);

    List<Message> loadRecentMessages(Long conversationId, int windowSize);

    List<Message> loadRecentMessagesBefore(Long conversationId, Long beforeMessageId, int windowSize);

    void ensureConversationOwnership(Long userId, Long conversationId);

    List<ConversationSummaryDTO> listConversations(Long userId);

    List<MessageDTO> listMessages(Long userId, Long conversationId);
}
