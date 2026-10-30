<template>
  <article class="shop-card card-panel hover-lift">
    <RouterLink :to="`/shop/${shop.id}`" class="cover-link">
      <img v-if="shop.cover" class="cover" :src="shop.cover" :alt="shop.name" loading="lazy" @error="$event.target.style.display = 'none'" />
      <span v-else class="cover-placeholder">LUMINA WALK · 暂无图片</span>
    </RouterLink>
    <div class="content">
      <div class="tag-row"><span v-for="tag in shop.tags" :key="tag" class="tag-chip">{{ tag }}</span><span v-if="shop.distance">{{ shop.distance }}</span></div>
      <h3><RouterLink :to="`/shop/${shop.id}`">{{ shop.name }}</RouterLink></h3>
      <p>{{ shop.address }}</p>
      <div class="bottom-row"><span>{{ shop.price > 0 ? `参考均价 ¥${shop.price}` : '探索地点详情' }}</span><el-button text @click="goDetail">查看地点 ↗</el-button></div>
    </div>
  </article>
</template>
<script setup>
const props = defineProps({
  shop: {
    type: Object,
    required: true
  }
});

const router = useRouter();

function goDetail() {
  router.push(`/shop/${props.shop.id}`);
}
</script>

<style scoped>
.shop-card { overflow:hidden; min-width:0; }
.cover-link { display:block; height:220px; background:linear-gradient(135deg,#dbe3dc,#edf0e8); }
.cover { width:100%; height:100%; object-fit:cover; }
.cover-placeholder { display:grid; place-items:center; height:100%; color:#52675c; font-size:12px; letter-spacing:2px; }
.content { padding:20px; }
.tag-row { display:flex; align-items:center; gap:10px; min-height:24px; color:#637368; font-size:12px; }
h3 { font-size:20px; margin:12px 0; line-height:1.5; }
p { font-size:13px; color:#68736d; line-height:1.7; min-height:44px; }
.bottom-row { display:flex; align-items:center; justify-content:space-between; border-top:1px solid var(--dp-border); padding-top:12px; font-size:12px; color:#68736d; }
</style>
