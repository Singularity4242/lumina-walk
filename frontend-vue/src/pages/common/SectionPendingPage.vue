<template>
  <div class="page-shell">
    <main class="page-container pending-page">
      <span class="eyebrow">LUMINA WALK / 寻光漫游</span>
      <h1>{{ page.title }}</h1>
      <p>{{ page.description }}</p>
      <RouterLink to="/" class="back-link">返回灵感画廊 ↗</RouterLink>
    </main>
  </div>
</template>

<script setup>
const route = useRoute();
const pages = {
  gear: { title: '器材', description: '器材门店独立页面正在整理。' },
  studios: { title: '专业影棚', description: '当前没有专业影棚演示数据，暂时无法展示影棚门店。' },
  offers: { title: '优惠活动', description: '秒杀券聚合页面正在整理。' }
};
const page = computed(() => pages[route.name] || { title: '页面', description: '内容暂时不可用。' });
</script>

<style scoped>
.pending-page { display:flex; min-height:60vh; flex-direction:column; align-items:center; justify-content:center; padding:70px 24px; text-align:center; }
.eyebrow { color:#68836c; font-size:11px; font-weight:700; letter-spacing:3px; }
h1 { margin:18px 0 10px; font-size:clamp(32px,5vw,54px); font-weight:600; }
p { max-width:520px; color:var(--dp-text-secondary); font-size:15px; line-height:1.9; }
.back-link { margin-top:18px; padding:12px 20px; border-radius:8px; background:var(--dp-primary); color:#fff; font-size:14px; }
</style>
