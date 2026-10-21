package com.luminawalk.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.luminawalk.ai.entity.AiConversation;
import com.luminawalk.ai.mapper.AiConversationMapper;
import com.luminawalk.ai.service.AiConversationService;
import org.springframework.stereotype.Service;

@Service
public class AiConversationServiceImpl extends ServiceImpl<AiConversationMapper, AiConversation> implements AiConversationService {
}
