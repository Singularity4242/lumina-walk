import { createRouter, createWebHashHistory } from 'vue-router';
import HomePage from '@/pages/home/HomePage.vue';
import CategoryPage from '@/pages/category/CategoryPage.vue';
import ShopDetailPage from '@/pages/shop/ShopDetailPage.vue';
import ProfilePage from '@/pages/profile/ProfilePage.vue';
import BlogDetailPage from '@/pages/blog/BlogDetailPage.vue';
import SectionPendingPage from '@/pages/common/SectionPendingPage.vue';
import OffersPage from '@/pages/offers/OffersPage.vue';
import AiAssistantPage from '@/pages/ai/AiAssistantPage.vue';

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomePage,
      meta: { tab: 'home', title: '灵感' }
    },
    {
      path: '/category/:id?',
      name: 'category',
      component: CategoryPage,
      meta: { tab: 'category', title: '宝藏地点' }
    },
    {
      path: '/blog/:id',
      name: 'blog-detail',
      component: BlogDetailPage,
      meta: { title: '摄影笔记' }
    },
    {
      path: '/gear',
      name: 'gear',
      component: SectionPendingPage,
      meta: { title: '器材' }
    },
    {
      path: '/studios',
      name: 'studios',
      component: SectionPendingPage,
      meta: { title: '专业影棚' }
    },
    {
      path: '/offers',
      name: 'offers',
      component: OffersPage,
      meta: { title: '优惠活动' }
    },
    {
      path: '/shop/:id',
      name: 'shop-detail',
      component: ShopDetailPage,
      meta: { title: '地点详情' }
    },
    {
      path: '/profile',
      name: 'profile',
      component: ProfilePage,
      meta: { tab: 'profile', title: '我的' }
    },
    {
      path: '/ai-assistant',
      name: 'ai-assistant',
      component: AiAssistantPage,
      meta: { tab: 'ai', title: '摄影助手' }
    }
  ],
  scrollBehavior() {
    return { top: 0 };
  }
});

export default router;
