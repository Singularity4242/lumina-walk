<template>
  <div class="app-root">
    <AppHeader />
    <RouterView v-slot="{ Component, route }">
      <Transition name="route-fade" mode="out-in">
        <component :is="Component" :key="route.fullPath" />
      </Transition>
    </RouterView>
    <LoginDialog />
  </div>
</template>

<script setup>
import AppHeader from '@/components/layout/AppHeader.vue';
import LoginDialog from '@/components/auth/LoginDialog.vue';
import { useUserStore } from '@/store/user';

const userStore = useUserStore();

onMounted(async () => {
  await userStore.initAuth();
  window.addEventListener('hm-auth-expired', handleAuthExpired);
});

onBeforeUnmount(() => {
  window.removeEventListener('hm-auth-expired', handleAuthExpired);
});

function handleAuthExpired() {
  userStore.clearAuth();
  userStore.openLoginDialog();
}
</script>

<style scoped>
.app-root {
  min-height: 100vh;
  background: var(--dp-bg-page);
}

.route-fade-enter-active,
.route-fade-leave-active {
  transition: all 0.24s ease;
}

.route-fade-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.route-fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
