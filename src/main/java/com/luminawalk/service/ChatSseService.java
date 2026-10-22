package com.luminawalk.service;

import com.luminawalk.dto.ChatStreamEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ChatSseService {

    SseEmitter connect(Long userId, Long conversationId);

    void send(Long userId, Long conversationId, ChatStreamEvent event);
}
