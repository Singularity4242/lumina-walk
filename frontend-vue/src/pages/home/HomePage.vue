<template>
  <div class="page-shell">
    <main class="page-container inspiration-page">
      <section class="gallery-section" aria-label="摄影笔记热榜">
        <div v-if="loading" class="gallery-loading" role="status" aria-live="polite">
          <div v-for="index in 6" :key="index" class="photo-skeleton"></div>
          <span class="sr-only">正在加载摄影笔记</span>
        </div>
        <div
          v-else-if="blogs.length"
          class="photo-gallery"
          :style="{ gridTemplateColumns: `repeat(${blogColumns.length}, minmax(0, 1fr))` }"
        >
          <div v-for="(column, columnIndex) in blogColumns" :key="columnIndex" class="photo-column">
            <RouterLink
              v-for="blog in column"
              :key="blog.id"
              :to="`/blog/${blog.id}`"
              class="photo-card"
              :aria-label="`阅读摄影笔记：${blog.title || '无标题笔记'}`"
            >
              <img :src="blog.cover" :alt="blog.title || '摄影笔记图片'" :loading="blog.loadEagerly ? 'eager' : 'lazy'" />
              <span class="photo-caption">
                <span class="photo-author">{{ blog.name || '摄影爱好者' }}</span>
                <strong>{{ blog.title || '无标题笔记' }}</strong>
              </span>
            </RouterLink>
          </div>
        </div>
        <div v-else class="empty-block">
          <p>{{ loadError ? '摄影笔记加载失败，请稍后重试。' : '暂时没有带图片的摄影笔记。' }}</p>
          <el-button v-if="loadError" type="primary" plain @click="loadBlogs">重新加载</el-button>
        </div>
        <p v-if="blogs.length" class="gallery-footnote">已加载 {{ blogs.length }} 篇带图摄影笔记</p>
      </section>
    </main>
    <BottomNav current-tab="home" />
  </div>
</template>

<script setup>
import BottomNav from '@/components/layout/BottomNav.vue';
import { blogApi } from '@/api/modules/blog';

const PAGE_SIZE = 10;
const MAX_SAMPLE_PAGES = 3;
const blogs = ref([]);
const loading = ref(false);
const loadError = ref(false);
const columnCount = ref(5);

const blogColumns = computed(() => {
  const count = Math.min(columnCount.value, Math.max(blogs.value.length, 1));
  const columns = Array.from({ length: count }, () => []);
  blogs.value.forEach((blog, index) => {
    columns[index % count].push({ ...blog, loadEagerly: index < count });
  });
  return columns;
});

onMounted(() => {
  syncColumnCount();
  window.addEventListener('resize', syncColumnCount);
  loadBlogs();
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncColumnCount);
});

async function loadBlogs() {
  if (loading.value) return;
  loading.value = true;
  loadError.value = false;
  const found = [];
  try {
    for (let page = 1; page <= MAX_SAMPLE_PAGES; page += 1) {
      const list = await blogApi.getHot(page);
      found.push(...(list || []));
      if ((list || []).length < PAGE_SIZE) break;
    }
    const unique = [...new Map(found.map((item) => [item.id, item])).values()];
    blogs.value = unique.map(normalizeBlog).filter((item) => item.cover);
  } catch {
    loadError.value = true;
    blogs.value = [...new Map(found.map((item) => [item.id, item])).values()].map(normalizeBlog).filter((item) => item.cover);
  } finally {
    loading.value = false;
  }
}

function normalizeBlog(blog) {
  return {
    ...blog,
    cover: String(blog?.images || '').split(',').map((item) => item.trim()).find(Boolean) || ''
  };
}

function syncColumnCount() {
  columnCount.value = window.innerWidth <= 700 ? 2 : 5;
}

</script>

<style scoped>
.inspiration-page { padding-top:0; }
.gallery-section { margin:0 0 24px; padding-top:16px; }
.photo-gallery { display:grid; gap:16px; align-items:start; }
.photo-column { display:grid; gap:16px; align-content:start; min-width:0; }
.photo-card { position:relative; display:block; width:100%; overflow:hidden; background:#f0f0f0; color:#fff; isolation:isolate; }
.photo-card img { display:block; width:100%; height:auto; min-height:120px; object-fit:cover; transition:transform .35s ease,filter .35s ease; }
.photo-card:hover img,.photo-card:focus-visible img { transform:scale(1.015); filter:brightness(.78); }
.photo-card::after { content:''; position:absolute; inset:38% 0 0; background:linear-gradient(transparent,rgba(0,0,0,.76)); opacity:0; transition:opacity .25s ease; pointer-events:none; }
.photo-card:hover::after,.photo-card:focus-visible::after { opacity:1; }
.photo-caption { position:absolute; z-index:1; right:18px; bottom:16px; left:18px; display:grid; gap:5px; opacity:0; transform:translateY(8px); transition:opacity .25s ease,transform .25s ease; }
.photo-card:hover .photo-caption,.photo-card:focus-visible .photo-caption { opacity:1; transform:translateY(0); }
.photo-caption strong { display:-webkit-box; overflow:hidden; -webkit-line-clamp:2; -webkit-box-orient:vertical; font-size:15px; font-weight:650; line-height:1.45; }
.photo-author { font-size:12px; opacity:.9; }
.gallery-footnote { margin:6px 0 0; color:#8b8b8b; font-size:12px; text-align:right; }
.gallery-loading { display:grid; grid-template-columns:repeat(5,1fr); gap:16px; }
.photo-skeleton { height:300px; background:linear-gradient(100deg,#eeeeee 20%,#f7f7f7 40%,#eeeeee 60%); background-size:200% 100%; animation:shimmer 1.5s linear infinite; }
.photo-skeleton:nth-child(2n) { height:390px; }
.sr-only { position:absolute; width:1px; height:1px; padding:0; margin:-1px; overflow:hidden; clip:rect(0,0,0,0); white-space:nowrap; border:0; }
@keyframes shimmer { to { background-position-x:-200%; } }
@media(max-width:1000px) { .photo-gallery,.photo-column { gap:12px; } .gallery-loading { grid-template-columns:repeat(5,1fr); gap:12px; } }
@media(max-width:700px) {
  .gallery-section { padding-top:8px; }
  .photo-gallery,.photo-column { gap:8px; }
  .gallery-loading { grid-template-columns:repeat(2,1fr); gap:8px; }
  .photo-caption { right:11px; bottom:10px; left:11px; }
  .photo-caption strong { font-size:13px; }
  .gallery-footnote { text-align:left; }
}
@media(hover:none) { .photo-card::after,.photo-caption { opacity:1; } .photo-caption { transform:none; } .photo-card::after { inset:52% 0 0; } }
@media(prefers-reduced-motion:reduce) { .photo-card img,.photo-skeleton { transition:none; animation:none; } }
</style>
