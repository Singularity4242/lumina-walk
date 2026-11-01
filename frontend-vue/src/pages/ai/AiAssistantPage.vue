<template>
  <main class="assistant-page">
    <aside class="assistant-sidebar">
      <div class="sidebar-brand"><span class="assistant-symbol" aria-hidden="true">✳</span><div><strong>摄影助手</strong><small>LUMINA WALK ASSISTANT</small></div></div>
      <el-button class="new-conversation" type="primary" :disabled="aiStore.busy" @click="aiStore.startNewConversation()">＋ 新建会话</el-button>
      <AiConversationPanel />
      <p class="sidebar-note">找地点、聊摄影、理解购券规则。具体信息以平台资料和门店约定为准。</p>
    </aside>

    <section class="assistant-workspace" aria-label="摄影助手对话">
      <header class="chat-header">
        <div><h1>{{ aiStore.currentConversationTitle }}</h1><span>{{ aiStore.pending ? '正在处理你的问题' : '摄影助手 · 对话' }}</span></div>
        <div class="chat-header-actions">
          <el-button v-if="aiStore.conversationId" size="small" :disabled="aiStore.busy" @click="aiStore.switchConversation(aiStore.conversationId)">刷新会话</el-button>
          <el-button size="small" :disabled="aiStore.busy" @click="aiStore.clearHistory()">重置窗口</el-button>
        </div>
      </header>
      <div ref="chatList" class="chat-list" @scroll="onScroll">
        <div v-if="!aiStore.history.length && !aiStore.historyLoading" class="welcome">
          <span class="welcome-symbol" aria-hidden="true">✳</span>
          <p class="welcome-kicker">从一个问题，开始下一次拍摄</p>
          <h2>今天，想探索什么？</h2>
          <p class="welcome-description">说说你的拍摄主题、城市或遇到的问题，<br />让摄影助手帮你整理信息与思路。</p>
          <div class="prompt-grid">
            <button v-for="item in suggestions" :key="item.title" @click="fillPrompt(item.prompt)">
              <span>{{ item.icon }}</span><strong>{{ item.title }}</strong><small>{{ item.description }}</small><i aria-hidden="true">↗</i>
            </button>
          </div>
          <p v-if="!userStore.isLoggedIn" class="login-hint">登录后可以提问，并查看自己的历史会话。<button @click="userStore.openLoginDialog()">去登录</button></p>
        </div>
        <p v-if="aiStore.historyLoading" class="history-loading" role="status">正在读取历史消息…</p>
        <div v-else class="message-stack">
          <AiBubble v-for="message in aiStore.history" :key="message.id" :message="message" />
        </div>
      </div>
      <div class="composer-area">
        <div v-if="aiStore.error" class="chat-error" role="alert">
          <p>{{ aiStore.error }}</p>
          <button v-if="aiStore.lastPrompt && !aiStore.busy" @click="fillPrompt(aiStore.lastPrompt)">重新编辑问题</button>
        </div>
        <div class="composer">
          <textarea ref="editor" v-model="inputValue" aria-label="输入摄影问题" rows="3" maxlength="2000" :disabled="aiStore.historyLoading"
            placeholder="输入摄影问题；查找地点时，请说明城市与拍摄主题…" @keydown="onEditorKeydown"></textarea>
          <div class="composer-actions">
            <span>{{ inputValue.length }}/2000 <span class="keyboard-hint">· Enter 发送，Shift + Enter 换行</span></span>
            <el-button v-if="aiStore.pending" @click="aiStore.stopReceiving()">停止接收</el-button>
            <el-button v-else type="primary" :disabled="!inputValue.trim() || aiStore.busy || aiStore.waiting" @click="submitMessage">发送 ↑</el-button>
          </div>
        </div>
        <p class="composer-note">回答可能不完整，请核对具体地点与券规则。重置窗口不删除历史会话。</p>
      </div>
    </section>
  </main>
</template>

<script setup>
import AiBubble from '@/components/ai/AiBubble.vue';
import AiConversationPanel from '@/components/ai/AiConversationPanel.vue';
import { useAiStore } from '@/store/ai';
import { useUserStore } from '@/store/user';

const aiStore = useAiStore();
const userStore = useUserStore();
const inputValue = ref('');
const editor = ref(null);
const chatList = ref(null);
let stickToBottom = true;
const suggestions = [
  { icon: '↗', title: '发现拍摄地点', description: '从城市和拍摄主题开始', prompt: '我想找适合拍摄的地点，请先问我所在城市和拍摄主题，再根据平台现有资料推荐。' },
  { icon: '◉', title: '整理地点信息', description: '了解地点资料与拍摄笔记', prompt: '我想了解一个拍摄地点，请先确认地点名称或 ID，再整理已有资料；没有资料的部分请明确说明。' },
  { icon: '☼', title: '交流摄影经验', description: '聊聊光线、构图与器材选择', prompt: '逆光人像如何控制曝光？请给我适合新手尝试的通用拍摄建议。' },
  { icon: '▤', title: '了解购券规则', description: '分清券售价、抵扣金额和使用条件', prompt: '器材租赁抵扣券的售价和可抵扣金额有什么区别？如果询问具体使用规则，请先确认我指的是哪张券，不要推测未提供的条款。' }
];

watch(() => userStore.profile?.id, (id, previous) => {
  if (previous != null && previous !== id) inputValue.value = '';
  if (id != null) void aiStore.bootstrapConversations();
}, { immediate: true });
watch(() => [aiStore.history.length, aiStore.history.at(-1)?.content, aiStore.history.at(-1)?.steps?.length], async () => {
  await nextTick();
  if (stickToBottom && chatList.value) chatList.value.scrollTop = chatList.value.scrollHeight;
});
onBeforeUnmount(() => aiStore.stopReceiving());

function onScroll() {
  const el = chatList.value;
  stickToBottom = !el || el.scrollHeight - el.scrollTop - el.clientHeight < 80;
}
function fillPrompt(prompt) { inputValue.value = prompt; editor.value?.focus(); }
function submitMessage() {
  if (aiStore.busy || aiStore.waiting || !inputValue.value.trim()) return;
  if (!userStore.isLoggedIn) { userStore.openLoginDialog(); return; }
  const prompt = inputValue.value.trim();
  inputValue.value = '';
  stickToBottom = true;
  void aiStore.sendMessage(prompt);
}
function onEditorKeydown(event) {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing || event.keyCode === 229) return;
  event.preventDefault();
  submitMessage();
}
</script>

<style scoped>
.assistant-page { display:grid; grid-template-columns:270px minmax(0,1fr); height:calc(100dvh - 81px); min-height:540px; }
.assistant-sidebar { padding:30px 22px 20px; background:#f8f8f7; border-right:1px solid #eaeaea; display:flex; flex-direction:column; gap:24px; min-height:0; overflow-y:auto; }
.sidebar-brand { display:flex; align-items:center; gap:12px; }
.assistant-symbol { font-size:34px; }
.sidebar-brand strong { font-size:17px; }.sidebar-brand small { display:block; color:#999; font-size:8px; letter-spacing:1.4px; margin-top:6px; }
.new-conversation { width:100%; min-height:42px; border-radius:8px; }
.sidebar-note { margin-top:auto; color:#888; font-size:12px; line-height:1.9; }
.assistant-workspace { min-width:0; min-height:0; display:flex; flex-direction:column; }
.chat-header { display:flex; justify-content:space-between; align-items:center; gap:16px; padding:22px 32px; border-bottom:1px solid #eee; }
.chat-header h1 { font-size:16px; margin:0 0 6px; overflow-wrap:anywhere; }.chat-header span { color:#999; font-size:11px; }.chat-header-actions { display:flex; flex-shrink:0; }
.chat-list { flex:1; min-height:0; overflow-y:auto; padding:24px 32px; scroll-behavior:auto; }
.welcome { max-width:720px; margin:22px auto; text-align:center; }.welcome-symbol { display:block; font-size:52px; }.welcome-kicker { font-size:12px; color:#888; margin:15px 0; letter-spacing:1px; }.welcome h2 { font-size:30px; font-weight:650; margin:12px 0; }.welcome-description { font-size:14px; line-height:1.9; color:#888; }
.prompt-grid { display:grid; grid-template-columns:1fr 1fr; gap:12px; margin-top:32px; text-align:left; }
.prompt-grid button { position:relative; padding:20px; border:1px solid #e7e7e7; border-radius:10px; background:white; text-align:left; cursor:pointer; color:#222; }.prompt-grid button:hover { background:#f8f8f8; border-color:#bbb; }.prompt-grid span { display:block; font-size:21px; margin-bottom:12px; }.prompt-grid strong { font-size:14px; }.prompt-grid small { display:block; color:#999; font-size:12px; margin-top:7px; }.prompt-grid i { position:absolute; right:20px; top:20px; font-style:normal; color:#888; }
.login-hint { font-size:12px; color:#888; margin-top:24px; }.login-hint button,.chat-error button { background:none; border:0; text-decoration:underline; cursor:pointer; font-size:12px; color:inherit; }
.message-stack { max-width:860px; margin:auto; }.history-loading { text-align:center; color:#888; font-size:13px; }
.composer-area { width:min(924px,100%); box-sizing:border-box; margin:auto; padding:12px 32px 16px; }.composer { padding:14px 16px; border:1px solid #dedede; border-radius:12px; box-shadow:0 4px 18px #00000004; }.composer:focus-within { border-color:#999; }
.composer textarea { display:block; width:100%; box-sizing:border-box; resize:none; border:0; outline:none; font:inherit; font-size:14px; line-height:1.8; color:#222; background:transparent; }.composer textarea::placeholder { color:#aaa; }.composer-actions { display:flex; justify-content:space-between; align-items:center; gap:8px; margin-top:8px; }.composer-actions > span { font-size:11px; color:#999; }.composer-note { margin:10px 0 0; color:#999; font-size:11px; text-align:center; line-height:1.6; }.chat-error { margin-bottom:10px; padding:10px 14px; background:#fff5f3; border-radius:7px; color:#a3312b; font-size:12px; line-height:1.8; }.chat-error p { margin:0; }
@media(max-width:1100px) { .assistant-page { height:calc(100dvh - 107px); grid-template-columns:240px minmax(0,1fr); }.assistant-sidebar { padding:24px 16px; }.chat-header { padding:18px 22px; } }
@media(max-width:767px) { .assistant-page { height:auto; min-height:calc(100dvh - 107px); grid-template-columns:1fr; }.assistant-sidebar { border-right:0; border-bottom:1px solid #eee; padding:14px 16px; gap:12px; }.sidebar-brand,.sidebar-note { display:none; }.new-conversation { width:auto; align-self:flex-start; }.assistant-workspace { min-height:650px; }.chat-header { padding:16px; }.chat-header h1 { font-size:14px; }.chat-header-actions { gap:4px; }.chat-header-actions .el-button { margin-left:0; padding:8px; }.chat-list { max-height:65dvh; padding:20px 16px; }.welcome { margin:0 auto; }.welcome h2 { font-size:25px; }.welcome-symbol { font-size:38px; }.prompt-grid { gap:10px; margin-top:20px; }.prompt-grid button { padding:14px; }.prompt-grid small { line-height:1.6; }.composer-area { padding:12px 16px 20px; }.keyboard-hint { display:none; } }
</style>
