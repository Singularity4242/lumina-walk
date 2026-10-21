package com.luminawalk.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.luminawalk.ai.entity.AiMessage;
import com.luminawalk.ai.mapper.AiMessageMapper;
import com.luminawalk.ai.service.AiMessageService;
import org.springframework.stereotype.Service;

@Service
public class AiMessageServiceImpl extends ServiceImpl<AiMessageMapper, AiMessage> implements AiMessageService {
}
