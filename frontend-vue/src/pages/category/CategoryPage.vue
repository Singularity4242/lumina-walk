<template>
  <div class="page-shell">
    <div class="page-container">
      <section class="category-banner card-panel">
        <div>
          <h1>{{ currentCategory?.name || '全部分类' }}</h1>
          <p>{{ currentCategory?.description || '从人像到风景，找到适合这次拍摄的地方。' }}</p>
        </div>
        <span class="banner-icon">{{ currentCategory?.icon || '🧭' }}</span>
      </section>

      <div class="category-tabs card-panel">
        <button
          v-for="item in categories"
          :key="item.id"
          class="tab-item hover-lift"
          :class="{ active: Number(item.id) === Number(currentCategoryId) }"
          @click="changeCategory(item.id)"
        >
          {{ item.icon }} {{ item.name }}
        </button>
      </div>

      <section>
        <div class="section-title">
          <h2>分类地点</h2>
          <p>共 {{ shopList.length }} 个已加载地点</p>
        </div>

        <p v-if="!shopList.length" class="empty-block">暂无可展示的地点。</p>
        <div class="shop-list">
          <ShopCard v-for="shop in shopList" :key="shop.id" :shop="shop" />
        </div>
      </section>
    </div>

    <BottomNav current-tab="category" />
  </div>
</template>

<script setup>
import { ElMessage } from 'element-plus';
import BottomNav from '@/components/layout/BottomNav.vue';
import ShopCard from '@/components/common/ShopCard.vue';
import { homeApi } from '@/api/modules/home';

const route = useRoute();
const router = useRouter();

const categories = ref([]);
const shopList = ref([]);

const currentCategoryId = computed(() => route.params.id || categories.value[0]?.id || '');
const currentCategory = computed(() => categories.value.find((item) => Number(item.id) === Number(currentCategoryId.value)));

onMounted(async () => {
  try { categories.value = await homeApi.getCategories(); }
  catch { ElMessage.error('地点分类加载失败，请稍后重试'); return; }
  if (!route.params.id && categories.value[0]?.id) {
    router.replace(`/category/${categories.value[0].id}`);
    return;
  }
  loadCategoryShops();
});

watch(
  () => route.params.id,
  () => {
    if (categories.value.length) {
      loadCategoryShops();
    }
  }
);

async function loadCategoryShops() {
  try {
    const result = await homeApi.searchShops({
      category: currentCategoryId.value,
    });
    shopList.value = result.list;
  } catch (error) {
    ElMessage.error(error?.message || '加载分类地点失败');
  }
}

function changeCategory(categoryId) {
  router.push(`/category/${categoryId}`);
}


</script>

<style scoped lang="scss">
.category-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px;
  margin-bottom: 16px;
  background: linear-gradient(135deg, #fff5ec 0%, #ffffff 100%);
}

.category-banner h1 {
  margin: 0 0 10px;
}

.category-banner p {
  margin: 0;
  color: #666;
}

.banner-icon {
  font-size: 56px;
}

.category-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 16px;
  margin-bottom: 16px;
}

.tab-item {
  padding: 10px 14px;
  border: 1px solid #eee;
  border-radius: 999px;
  background: #fff;
  cursor: pointer;
}

.tab-item.active {
  color: #fff;
  border-color: var(--dp-primary);
  background: var(--dp-primary);
}

.shop-list {
  display: grid;
  gap: 14px;
}
</style>

<style scoped>
.shop-list { grid-template-columns:repeat(3,minmax(0,1fr)); gap:22px; }
.category-banner { background:#e9eee3; padding:32px; }
@media(max-width:1000px) { .shop-list { grid-template-columns:repeat(2,minmax(0,1fr)); } }
@media(max-width:600px) { .shop-list { grid-template-columns:1fr; } }
</style>
