<template>
  <div class="page-shell">
    <main class="page-container note-page">
      <RouterLink to="/" class="back-link">← 返回灵感</RouterLink>

      <div v-if="loading" class="note-state" role="status">正在加载摄影笔记…</div>
      <div v-else-if="loadError" class="note-state">
        <p>笔记暂时无法加载，或已不存在。</p>
        <el-button type="primary" plain @click="loadBlog">重新加载</el-button>
      </div>
      <article v-else-if="blog" class="note-article">
        <header class="note-header">
          <span class="eyebrow">PHOTO STORY / 摄影笔记</span>
          <h1>{{ blog.title || '无标题笔记' }}</h1>
          <div class="note-meta">
            <span>{{ blog.name || '摄影爱好者' }}</span>
            <span v-if="formattedDate">{{ formattedDate }}</span>
            <span>点赞 {{ blog.liked || 0 }}</span>
          </div>
        </header>

        <div v-if="blog.imageList.length" class="note-images">
          <figure v-for="(image, index) in blog.imageList" :key="`${image}-${index}`">
            <img :src="image" :alt="`${blog.title || '摄影笔记'}，图片 ${index + 1}`" />
          </figure>
        </div>
        <p v-else class="no-image">这篇笔记没有图片。</p>

        <div class="note-body">
          <p>{{ blog.content || '作者暂未填写正文。' }}</p>
          <div class="note-actions">
            <RouterLink v-if="blog.shopId" :to="`/shop/${blog.shopId}`" class="place-link">查看关联地点 ↗</RouterLink>
            <el-button :type="blog.isLike ? 'success' : 'primary'" plain @click="toggleLike">
              {{ blog.isLike ? '已点赞' : '点赞这篇笔记' }}
            </el-button>
          </div>
        </div>
      </article>
    </main>
  </div>
</template>

<script setup>
import { ElMessage } from 'element-plus';
import { blogApi } from '@/api/modules/blog';
import { useUserStore } from '@/store/user';

const route = useRoute();
const userStore = useUserStore();
const blog = ref(null);
const loading = ref(false);
const loadError = ref(false);

const formattedDate = computed(() => {
  if (!blog.value?.createTime) return '';
  const date = new Date(blog.value.createTime);
  return Number.isNaN(date.getTime()) ? '' : new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' }).format(date);
});

onMounted(loadBlog);

async function loadBlog() {
  loading.value = true;
  loadError.value = false;
  try {
    const detail = await blogApi.getDetail(route.params.id);
    blog.value = {
      ...detail,
      isLike: Boolean(detail?.isLike),
      imageList: String(detail?.images || '').split(',').map((item) => item.trim()).filter(Boolean)
    };
  } catch {
    blog.value = null;
    loadError.value = true;
  } finally {
    loading.value = false;
  }
}

async function toggleLike() {
  if (!userStore.isLoggedIn) {
    userStore.openLoginDialog();
    return;
  }
  try {
    await blogApi.toggleLike(blog.value.id);
    const next = !blog.value.isLike;
    blog.value = { ...blog.value, isLike: next, liked: Math.max(0, Number(blog.value.liked || 0) + (next ? 1 : -1)) };
    ElMessage.success(next ? '点赞成功' : '已取消点赞');
  } catch (error) {
    ElMessage.error(error?.message || '更新点赞状态失败');
  }
}
</script>

<style scoped>
.note-page { max-width:940px; padding-top:36px; }
.back-link { display:inline-flex; padding:8px 0; color:var(--dp-primary); font-size:14px; }
.note-article { margin-top:32px; }
.note-header { margin-bottom:32px; text-align:center; }
.eyebrow { color:#66816d; font-size:11px; font-weight:700; letter-spacing:3px; }
.note-header h1 { margin:15px auto 18px; font-size:clamp(28px,4vw,44px); font-weight:600; line-height:1.4; }
.note-meta { display:flex; justify-content:center; flex-wrap:wrap; gap:18px; color:#748077; font-size:13px; }
.note-images { display:grid; gap:20px; }
.note-images figure { margin:0; display:flex; justify-content:center; overflow:hidden; border-radius:12px; background:#e6ebe3; }
.note-images img { display:block; max-width:100%; height:auto; object-fit:contain; }
.no-image { padding:56px 20px; border-radius:12px; background:#e9eee7; color:#718075; text-align:center; }
.note-body { max-width:740px; margin:42px auto 0; }
.note-body > p { margin:0; color:#34463b; font-size:16px; line-height:2; white-space:pre-wrap; overflow-wrap:anywhere; }
.note-actions { display:flex; justify-content:space-between; align-items:center; gap:20px; margin-top:36px; padding-top:20px; border-top:1px solid var(--dp-border); }
.place-link { color:var(--dp-primary); font-size:14px; }
.note-state { display:grid; justify-items:center; gap:16px; margin:70px 0; padding:60px 20px; border-radius:16px; background:#e9eee7; color:#5e6c62; text-align:center; }
@media(max-width:600px) { .note-page { padding-top:20px; } .note-article { margin-top:20px; } .note-header { margin-bottom:22px; } .note-images { gap:12px; } .note-body { margin-top:26px; } .note-actions { align-items:flex-start; flex-direction:column; } }
</style>
