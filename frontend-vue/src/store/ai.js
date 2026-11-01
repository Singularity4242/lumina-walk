import { computed, ref, watch } from 'vue';
import { defineStore } from 'pinia';
import { aiApi } from '@/api/modules/ai';
import { useUserStore } from '@/store/user';

const STATUS_LABELS = {
  queued: '问题已收到，等待处理', thinking: '正在分析问题', executing: '正在查询资料',
  answering: '正在生成回答', finished: '资料处理完成', error: '处理出现异常',
  stream_fallback: '正在恢复回答', stream_recover: '正在补全回答'
};
function normalizeMessages(items) {
  return items.filter(item => ['user', 'assistant', 'tool'].includes(item.role)).map(item => ({
    id: String(item.id), role: item.role, content: item.role === 'tool' ? '' : (item.content || ''),
    toolName: item.toolName, createdTime: item.createdTime, state: 'complete', steps: []
  }));
}
function delay(ms, signal) {
  return new Promise(resolve => {
    const finish = () => { clearTimeout(timer); signal?.removeEventListener('abort', finish); resolve(); };
    const timer = setTimeout(finish, ms);
    if (signal?.aborted) finish();
    else signal?.addEventListener('abort', finish, { once: true });
  });
}

export const useAiStore = defineStore('ai', () => {
  const userStore = useUserStore();
  const conversations = ref([]);
  const conversationId = ref(null);
  const history = ref([]);
  const pending = ref(false);
  const listLoading = ref(false);
  const historyLoading = ref(false);
  const listError = ref('');
  const error = ref('');
  const waiting = ref(false);
  const lastPrompt = ref('');
  const drawerVisible = ref(false);
  let generation = 0;
  let controller;
  let listGeneration = 0;
  const busy = computed(() => pending.value || historyLoading.value);
  const currentConversationTitle = computed(() => conversations.value.find(c => c.id === conversationId.value)?.title || '新的摄影对话');

  function ensureLoggedIn() {
    if (userStore.isLoggedIn) return true;
    userStore.openLoginDialog();
    return false;
  }
  function stopReceiving() {
    generation += 1;
    controller?.abort();
    controller = null;
    if (pending.value) {
      const message = history.value.findLast(item => item.state === 'pending');
      if (message) { message.state = 'interrupted'; message.statusText = '已停止接收'; }
      waiting.value = Boolean(conversationId.value);
      error.value = '已停止接收。后台可能仍在处理，请刷新会话查看结果，或新建会话。';
    }
    pending.value = false;
    historyLoading.value = false;
  }
  function clearHistory() {
    stopReceiving();
    conversationId.value = null;
    history.value = [];
    waiting.value = false;
    error.value = '';
    lastPrompt.value = '';
  }
  watch(() => userStore.profile?.id, () => {
    clearHistory();
    listGeneration += 1;
    listLoading.value = false;
    conversations.value = [];
    listError.value = '';
    drawerVisible.value = false;
  }, { flush: 'sync' });

  async function loadConversations() {
    if (!userStore.isLoggedIn) return;
    const run = ++listGeneration;
    listLoading.value = true;
    listError.value = '';
    try {
      const data = await aiApi.listConversations();
      if (run !== listGeneration) return;
      if (!Array.isArray(data)) throw new Error('会话列表格式异常');
      conversations.value = data.map(item => ({ ...item, id: String(item.id) }));
    } catch (e) {
      if (run === listGeneration) listError.value = e.message || '历史会话加载失败，请重试';
    } finally { if (run === listGeneration) listLoading.value = false; }
  }
  async function startNewConversation() {
    if (busy.value || !ensureLoggedIn()) return;
    clearHistory();
    const run = generation;
    historyLoading.value = true;
    try {
      const data = await aiApi.createConversation();
      if (run !== generation) return;
      if (!data?.id) throw new Error('创建会话失败，请重试');
      const item = { ...data, id: String(data.id) };
      conversationId.value = item.id;
      conversations.value.unshift(item);
    } catch (e) {
      if (run === generation) error.value = e.message || '创建会话失败，请重试';
    } finally { if (run === generation) historyLoading.value = false; }
  }
  async function switchConversation(id) {
    if (pending.value || !ensureLoggedIn()) return;
    stopReceiving();
    const run = generation;
    historyLoading.value = true;
    error.value = '';
    try {
      const data = await aiApi.getMessages(id);
      if (run !== generation) return;
      if (!Array.isArray(data)) throw new Error('历史消息格式异常');
      history.value = normalizeMessages(data);
      conversationId.value = String(id);
      const last = history.value.findLast(item => item.role !== 'tool');
      waiting.value = last?.role === 'user';
      if (waiting.value) error.value = '这条问题还没有返回回答，请稍后刷新会话，或新建会话。';
      lastPrompt.value = history.value.findLast(item => item.role === 'user')?.content || '';
    } catch (e) {
      if (run === generation) error.value = e.message || '历史消息加载失败，请重试';
    } finally { if (run === generation) historyLoading.value = false; }
  }

  async function sendMessage(content) {
    const prompt = content?.trim();
    if (!prompt || busy.value || waiting.value || !ensureLoggedIn()) return;
    controller = new AbortController();
    const signal = controller.signal;
    const run = ++generation;
    const active = () => run === generation && !signal.aborted;
    pending.value = true;
    error.value = '';
    lastPrompt.value = prompt;
    const userMessage = { id: `local-user-${run}`, role: 'user', content: prompt };
    const reply = { id: `local-assistant-${run}`, role: 'assistant', content: '', state: 'pending', statusText: '正在连接助手', steps: [] };
    history.value.push(userMessage, reply);
    const currentReply = () => history.value.find(item => item.id === reply.id);
    let posted = false;
    let streamController;
    let settle;
    let connectTimer;
    let replyTimer;
    try {
      if (!conversationId.value) {
        const conversation = await aiApi.createConversation(signal);
        if (!active()) return;
        if (!conversation?.id) throw new Error('创建会话失败');
        conversationId.value = String(conversation.id);
        conversations.value.unshift({ ...conversation, id: conversationId.value });
      }
      const id = conversationId.value;
      streamController = new AbortController();
      signal.addEventListener('abort', () => streamController.abort(), { once: true });
      let ready;
      const connected = new Promise(resolve => { ready = resolve; });
      const outcome = new Promise(resolve => { settle = resolve; });
      signal.addEventListener('abort', () => { ready(false); settle({ kind: 'cancelled' }); }, { once: true });
      // 先订阅再发送。断流恢复只读取历史，不重复 POST。
      void aiApi.streamConversation(id, {
        signal: streamController.signal,
        onOpen: () => ready(true),
        onEvent: event => {
          if (!active() || !posted) return;
          const envelope = event.data;
          if (envelope?.conversationId != null && String(envelope.conversationId) !== id) return;
          const payload = envelope?.payload ?? envelope ?? {};
          const target = currentReply();
          if (!target) return;
          if (event.event === 'status') {
            target.statusText = STATUS_LABELS[payload.status] || '正在处理';
            const tools = Array.isArray(payload.toolNames) ? payload.toolNames.join('、') : '';
            const label = `${payload.step ? `第 ${payload.step} 轮 · ` : ''}${target.statusText}${tools ? `：${tools}` : ''}`;
            if (target.steps.at(-1) !== label) target.steps.push(label);
          } else if (event.event === 'answer_delta') {
            target.statusText = '正在生成回答';
            target.content += payload.delta || '';
          } else if (event.event === 'message' && payload.role === 'tool') {
            target.steps.push(`已返回工具结果：${payload.toolName || '资料查询'}`);
          } else if (event.event === 'message' && payload.role === 'assistant') {
            target.content = payload.content || target.content;
          } else if (event.event === 'answer_done') {
            target.content = payload.content || target.content;
            settle({ kind: payload.status === 'error' ? 'failed' : 'complete' });
          } else if (event.event === 'done') {
            settle({ kind: payload.status === 'error' ? 'failed' : target.content ? 'complete' : 'disconnected' });
          } else if (event.event === 'error') {
            settle({ kind: 'failed' });
          }
        }
      }).then(() => settle({ kind: 'disconnected' })).catch(() => {
        ready(false);
        settle({ kind: 'disconnected' });
      });
      const isConnected = await Promise.race([connected, new Promise(resolve => { connectTimer = setTimeout(() => resolve(false), 4000); })]);
      clearTimeout(connectTimer);
      if (!active()) return;
      if (!isConnected) streamController.abort();
      posted = true;
      const receipt = await aiApi.chat({ conversationId: id, prompt }, signal);
      if (!active()) return;
      if (!receipt?.chatMessageId || String(receipt.conversationId) !== id) throw new Error('消息受理结果未确认，请刷新会话查看');
      const localUser = history.value.find(item => item.id === userMessage.id);
      if (localUser) localUser.id = String(receipt.chatMessageId);
      let result = await Promise.race([outcome, new Promise(resolve => { replyTimer = setTimeout(() => resolve({ kind: 'timeout' }), 75000); })]);
      clearTimeout(replyTimer);
      streamController.abort();
      if (!active()) return;
      if (result.kind === 'disconnected') {
        currentReply().statusText = '连接中断，正在查询已保存的回答';
        for (let attempt = 0; attempt < 30 && active(); attempt++) {
          const messages = await aiApi.getMessages(id, signal);
          if (!active()) return;
          const index = messages.findIndex(item => String(item.id) === String(receipt.chatMessageId));
          const answer = index < 0 ? null : messages.slice(index + 1).find(item => item.role === 'assistant');
          if (answer) { currentReply().content = answer.content || ''; result = { kind: 'complete' }; break; }
          await delay(2000, signal);
        }
      }
      if (!active()) return;
      const target = currentReply();
      if (result.kind === 'complete' && target.content.trim()) {
        target.state = 'complete';
        target.statusText = '回答已完成';
      } else {
        target.state = 'error';
        waiting.value = result.kind !== 'failed';
        error.value = result.kind === 'failed' ? '助手处理失败。可以修改问题后重试，或刷新会话查看已保存的消息。'
          : '暂未收到完整回答，后台可能仍在处理。请刷新会话查看结果，或新建会话。';
      }
      void loadConversations();
    } catch (e) {
      if (active()) {
        const target = currentReply();
        if (target) target.state = 'error';
        waiting.value = posted;
        error.value = posted ? `${e.message || '连接失败'}。请先刷新会话确认结果，避免重复发送。` : (e.message || '助手暂不可用，请重试');
      }
    } finally {
      clearTimeout(connectTimer);
      clearTimeout(replyTimer);
      streamController?.abort();
      if (active()) { pending.value = false; controller = null; }
    }
  }

  return {
    conversations, conversationId, history, pending, listLoading, historyLoading, listError, error,
    waiting, lastPrompt, busy, currentConversationTitle, drawerVisible,
    startNewConversation, switchConversation, sendMessage, stopReceiving, clearHistory,
    bootstrapConversations: loadConversations, refreshConversationList: loadConversations,
    sendQuickScene: sendMessage,
    openDrawer: () => { drawerVisible.value = true; void loadConversations(); }
  };
});
