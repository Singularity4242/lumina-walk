<template>
  <header class="site-header">
    <div class="header-inner">
      <RouterLink to="/" class="brand" aria-label="寻光漫游，前往灵感首页">
        <span class="brand-mark" aria-hidden="true"><i></i></span>
        <span class="brand-name">寻光漫游<small>LUMINA WALK</small></span>
      </RouterLink>

      <nav class="main-nav" aria-label="主导航">
        <RouterLink
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="nav-link"
          :class="{ active: isActive(item.path) }"
          :aria-current="isActive(item.path) ? 'page' : undefined"
        >{{ item.label }}</RouterLink>
      </nav>

      <button class="user-entry" type="button" :aria-label="userStore.isLoggedIn ? '打开个人中心' : '登录'" @click="handleUserClick">
        <el-avatar v-if="userStore.isLoggedIn" :src="userStore.displayAvatar" :size="26" />
        <span class="user-name">{{ userStore.isLoggedIn ? userStore.displayName : '登录' }}</span>
      </button>
    </div>
  </header>
</template>

<script setup>
import { useUserStore } from '@/store/user';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const navItems = [
  { label: '灵感', path: '/' },
  { label: '宝藏地点', path: '/category' },
  { label: '器材', path: '/gear' },
  { label: '专业影棚', path: '/studios' },
  { label: '优惠活动', path: '/offers' },
  { label: '摄影助手', path: '/ai-assistant' }
];

function isActive(path) {
  if (path === '/') return route.path === '/' || route.path.startsWith('/blog/');
  if (path === '/category') return route.path.startsWith('/category');
  return route.path === path;
}

function handleUserClick() {
  if (userStore.isLoggedIn) router.push('/profile');
  else userStore.openLoginDialog();
}
</script>

<style scoped>
.site-header { position:sticky; top:0; z-index:50; background:rgba(255,255,255,.96); border-bottom:1px solid #ededed; backdrop-filter:blur(14px); }
.header-inner { width:min(1840px,calc(100% - 48px)); min-height:80px; margin:auto; display:grid; grid-template-columns:auto minmax(0,1fr) auto; align-items:center; gap:44px; }
.brand { display:flex; align-items:center; gap:11px; white-space:nowrap; font-size:18px; font-weight:750; letter-spacing:1px; }
.brand-mark { position:relative; width:31px; height:31px; flex:none; border:2px solid #111; border-radius:50%; }
.brand-mark::before,.brand-mark::after,.brand-mark i { content:''; position:absolute; inset:7px; border:2px solid #111; border-radius:50%; }
.brand-mark::before { transform:translate(5px,-2px); }
.brand-mark::after { transform:translate(-4px,3px); }
.brand-mark i { display:block; transform:translate(1px,4px); }
.brand-name small { display:block; margin-top:3px; color:#777; font-size:8px; font-weight:600; letter-spacing:2.5px; }
.main-nav { display:flex; align-items:center; justify-content:flex-start; gap:8px; min-width:0; height:80px; }
.nav-link { display:inline-flex; align-items:center; min-height:46px; padding:0 18px; border-radius:14px; white-space:nowrap; color:#222; font-size:15px; font-weight:650; transition:background-color .2s ease,color .2s ease; }
.nav-link:hover,.nav-link.active { color:#111; background:#f2f2f2; }
.user-entry { display:flex; align-items:center; justify-content:center; gap:8px; min-width:72px; min-height:46px; padding:0 18px; border:1px solid #111; border-radius:14px; background:#111; color:#fff; cursor:pointer; white-space:nowrap; transition:background-color .2s ease,transform .2s ease; }
.user-entry:hover { background:#333; }
.user-entry:active { transform:translateY(1px); }
.user-name { max-width:88px; overflow:hidden; text-overflow:ellipsis; font-size:14px; font-weight:650; }
@media(max-width:1100px) {
  .header-inner { width:calc(100% - 28px); grid-template-columns:1fr auto; gap:0 12px; min-height:0; padding-top:12px; }
  .brand { font-size:16px; }
  .brand-mark { width:28px; height:28px; }
  .main-nav { grid-row:2; grid-column:1 / -1; justify-content:flex-start; gap:4px; width:100%; height:54px; overflow-x:auto; scrollbar-width:none; }
  .main-nav::-webkit-scrollbar { display:none; }
  .nav-link { min-height:38px; padding:0 13px; border-radius:11px; font-size:14px; }
  .user-entry { min-width:64px; min-height:40px; padding:0 14px; border-radius:12px; }
}
@media(max-width:480px) { .header-inner { width:calc(100% - 24px); } .user-entry .el-avatar { display:none; } }
</style>
