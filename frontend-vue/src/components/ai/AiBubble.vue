<template>
  <div v-if="message.role === 'tool'" class="tool-record">↳ 工具记录：{{ message.toolName || '资料查询' }}</div>
  <article v-else class="bubble-row" :class="message.role">
    <span class="message-avatar" aria-hidden="true">{{ message.role === 'user' ? '你' : '✳' }}</span>
    <div class="bubble">
      <span class="message-label">{{ message.role === 'user' ? '你' : '摄影助手' }}</span>
      <details v-if="message.steps?.length" class="execution-details" :open="message.state === 'pending'">
        <summary>处理记录 · {{ message.steps.length }} 条</summary>
        <ol><li v-for="(step, index) in message.steps" :key="index">{{ step }}</li></ol>
      </details>
      <p v-if="message.content" class="message-text">{{ message.content }}</p>
      <p v-if="message.state === 'pending'" class="message-status" role="status"><span class="status-dot"></span>{{ message.statusText || '正在处理' }}</p>
      <p v-else-if="message.state === 'error'" class="message-status error">{{ message.content ? '本次回答未正常完成，请查看下方提示。' : '暂未收到完整回答，请查看下方提示。' }}</p>
      <p v-else-if="message.state === 'interrupted'" class="message-status">接收已停止，可刷新会话查看后续结果。</p>
    </div>
  </article>
</template>

<script setup>
defineProps({ message: { type: Object, required: true } });
</script>

<style scoped>
.bubble-row { display:flex; gap:12px; margin-bottom:26px; }.bubble-row.user { flex-direction:row-reverse; }.message-avatar { flex:none; width:30px; height:30px; display:grid; place-items:center; border-radius:50%; background:#f1f1f1; font-size:17px; }.user .message-avatar { background:#222; color:#fff; font-size:11px; }.bubble { max-width:calc(100% - 42px); min-width:0; }.user .bubble { max-width:85%; }.message-label { display:block; font-size:11px; color:#888; margin:8px 0 10px; }.user .message-label { text-align:right; }.message-text { white-space:pre-wrap; overflow-wrap:anywhere; line-height:1.9; font-size:14px; margin:0; color:#333; }.user .message-text { background:#f3f3f3; border-radius:12px 0 12px 12px; padding:12px 16px; }.message-status { font-size:12px; color:#888; margin:12px 0 0; line-height:1.8; }.status-dot { display:inline-block; width:6px; height:6px; background:#777; border-radius:50%; margin-right:7px; }.error { color:#b23c32; }.execution-details { font-size:11px; color:#777; margin-bottom:12px; max-width:100%; overflow-wrap:anywhere; }.execution-details summary { cursor:pointer; }.execution-details ol { margin:10px 0; padding:10px 10px 10px 28px; border-left:2px solid #ddd; background:#fafafa; line-height:1.9; max-height:180px; overflow:auto; }.tool-record { margin:8px 0 14px 42px; font-size:11px; color:#888; overflow-wrap:anywhere; }
</style>
