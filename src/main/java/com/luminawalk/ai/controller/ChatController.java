package com.luminawalk.ai.controller;

import com.luminawalk.ai.repository.ChatHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.util.MimeType;
import org.springframework.ai.model.Media;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;

@RequiredArgsConstructor
@RestController
@RequestMapping("/ai")
public class ChatController {
    private final ChatClient assistantChatClient;

    private final ChatHistoryRepository chatHistoryRepository;

    @PostMapping(value = "/chat", produces = "text/html;charset=utf-8")
    public Flux<String> chat(
            @RequestParam("prompt") String prompt,
            @RequestParam("chatId") String chatId,
            @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        // 1.保存会话id
        chatHistoryRepository.save("chat", chatId);

        // 2.构建请求
        ChatClient.ChatClientRequestSpec request = assistantChatClient.prompt()
                .user(u -> {
                    u.text(prompt);
                    if (files != null && !files.isEmpty()) {
                        List<Media> medias = files.stream()
                                .map(file -> new Media(
                                        MimeType.valueOf(Objects.requireNonNull(file.getContentType())),
                                        file.getResource()
                                ))
                                .toList();
                        u.media(medias.toArray(Media[]::new));
                    }
                })
                .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId));

        // 3.流式返回内容
        return request.stream().content();
    }
}
