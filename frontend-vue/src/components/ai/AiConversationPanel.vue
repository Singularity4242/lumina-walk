<template>
  <section class="conversation-panel" aria-label="历史会话">
    <div class="panel-header"><strong>历史会话</strong><button :disabled="aiStore.busy || aiStore.listLoading" @click="aiStore.refreshConversationList()">{{ aiStore.listLoading ? '加载中…' : '刷新列表' }}</button></div>
    <p v-if="aiStore.listError" class="list-error" role="alert">{{ aiStore.listError }}</p>
    <div v-if="aiStore.conversations.length" class="conversation-list">
      <button v-for="item in aiStore.conversations" :key="item.id" class="conversation-item"
        :class="{ active: aiStore.conversationId === item.id }" :aria-pressed="aiStore.conversationId === item.id"
        :disabled="aiStore.busy" @click="aiStore.switchConversation(item.id)">
        <strong>{{ item.title || '新会话' }}</strong><span>{{ formatTime(item.updatedTime || item.createdTime) }}</span>
      </button>
    </div>
    <p v-else-if="!aiStore.listLoading && !aiStore.listError" class="list-empty">{{ userStore.isLoggedIn ? '暂无历史会话，开始第一个问题吧。' : '登录后查看历史会话。' }}</p>
  </section>
</template>

<script setup>
import { useAiStore } from '@/store/ai';
import { useUserStore } from '@/store/user';
const aiStore = useAiStore();
const userStore = useUserStore();
function formatTime(value) { return value ? String(value).replace('T', ' ').slice(5, 16) : ''; }
</script>

<style scoped>
.conversation-panel { min-width:0; }.panel-header { display:flex; align-items:center; justify-content:space-between; gap:8px; margin-bottom:14px; }.panel-header strong { font-size:12px; color:#777; font-weight:500; }.panel-header button { padding:4px 0; background:none; border:0; color:#888; font-size:11px; cursor:pointer; }.panel-header button:disabled { cursor:default; opacity:.5; }.conversation-list { display:grid; gap:7px; }.conversation-item { display:grid; text-align:left; gap:7px; width:100%; padding:12px; border:1px solid transparent; border-radius:8px; background:transparent; cursor:pointer; }.conversation-item:hover { background:#eee; }.conversation-item.active { background:white; border-color:#ddd; }.conversation-item strong { color:#444; font-size:13px; font-weight:500; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }.conversation-item span { color:#aaa; font-size:10px; }.conversation-item:disabled { cursor:default; opacity:.65; }.list-empty,.list-error { font-size:12px; line-height:1.8; color:#999; }.list-error { color:#ae4035; }@media(max-width:767px) { .conversation-list { display:flex; overflow-x:auto; padding-bottom:3px; }.conversation-item { min-width:170px; max-width:220px; flex-shrink:0; }.panel-header { margin-bottom:8px; }.list-empty { margin:0; } }
</style>
