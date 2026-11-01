import request from '@/api/request';

function buildApiPath(path) {
  return import.meta.env.DEV ? `/api${path}` : path;
}

function parseSseBlock(block) {
  const lines = block.split(/\r?\n/);
  let event = 'message';
  const dataLines = [];

  for (const line of lines) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim();
    }
    if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart());
    }
  }

  if (!dataLines.length) {
    return null;
  }

  const raw = dataLines.join('\n');
  try {
    return {
      event,
      data: JSON.parse(raw)
    };
  } catch {
    return {
      event,
      data: raw
    };
  }
}

export const aiApi = {
  createConversation(signal) {
    return request({
      url: '/ai/agent/conversations',
      method: 'post', signal
    });
  },
  listConversations(signal) {
    return request({
      url: '/ai/agent/conversations',
      method: 'get', signal
    });
  },
  getMessages(conversationId, signal) {
    return request({
      url: `/ai/agent/conversations/${conversationId}/messages`,
      method: 'get', signal
    });
  },
  chat(data, signal) {
    return request({
      url: '/ai/agent/chat',
      method: 'post',
      data, signal
    });
  },
  recommend(data) {
    return request({
      url: '/ai/agent/recommend',
      method: 'post',
      data
    });
  },
  analyzeShop(shopId) {
    return request({
      url: `/ai/agent/analyze/shop/${shopId}`,
      method: 'get'
    });
  },
  async streamConversation(conversationId, { signal, onEvent, onOpen } = {}) {
    const token = localStorage.getItem('hm-dianping-token');
    const response = await fetch(buildApiPath(`/ai/agent/conversations/${conversationId}/stream`), {
      method: 'GET',
      headers: {
        Accept: 'text/event-stream',
        ...(token ? { authorization: token } : {})
      },
      signal
    });

    if (response.status === 401) {
      localStorage.removeItem('hm-dianping-token');
      window.dispatchEvent(new CustomEvent('hm-auth-expired'));
      throw new Error('登录已过期，请重新登录');
    }
    if (!response.ok || !response.body || !response.headers.get('content-type')?.includes('text/event-stream')) {
      throw new Error('建立 AI 流式连接失败');
    }

    onOpen?.();

    const reader = response.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';

    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) {
          buffer += decoder.decode();
          if (buffer.trim()) {
            const parsed = parseSseBlock(buffer);
            if (parsed) onEvent?.(parsed);
          }
          break;
        }

        buffer += decoder.decode(value, { stream: true });
        const blocks = buffer.split(/\r?\n\r?\n/g);
        buffer = blocks.pop() || '';

        blocks
          .map((item) => item.trim())
          .filter(Boolean)
          .forEach((item) => {
            const parsed = parseSseBlock(item);
            if (parsed) onEvent?.(parsed);
          });
      }
    } finally {
      await reader.cancel().catch(() => {});
      reader.releaseLock();
    }
  }
};
