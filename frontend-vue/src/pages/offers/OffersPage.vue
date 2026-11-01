<template>
  <main class="page-shell">
    <div class="page-container offers-page">
      <header class="offers-heading">
        <div><p class="eyebrow">LUMINA WALK / OFFERS</p><h1>器材优惠券</h1><p>为下一次拍摄，选一份合适的优惠。</p></div>
        <el-button :loading="loading" @click="loadOffers(current)">刷新列表</el-button>
      </header>
      <p class="demo-note">演示购券 · 支付不产生真实扣款，购券不代表器材已预留。适用范围与使用约定请查看各券规则。</p>

      <div v-if="loadError" class="empty-block" role="alert">
        <p>{{ loadError }}</p><el-button @click="loadOffers(current)">重新加载</el-button>
      </div>
      <div v-else-if="loading" class="empty-block" role="status">正在加载优惠券…</div>
      <div v-else-if="!vouchers.length" class="empty-block">暂无上架的优惠券，请稍后再来看看。</div>
      <section v-else class="voucher-list" aria-label="优惠券列表">
        <article v-for="voucher in vouchers" :key="voucher.id" class="voucher-row">
          <RouterLink :to="`/shop/${voucher.shopId}`" class="voucher-shop">
            <img v-if="cover(voucher) && !brokenImages[voucher.id]" :src="cover(voucher)" :alt="voucher.shopName" loading="lazy" @error="brokenImages[voucher.id] = true" />
            <span v-else class="shop-placeholder">暂无店铺图片</span>
            <div><span class="subtle">适用门店</span><h2>{{ voucher.shopName }}</h2><span class="shop-link">查看门店 ↗</span></div>
          </RouterLink>
          <div class="voucher-info">
            <span class="type-badge">{{ Number(voucher.type) === 1 ? '限时抢购' : '日常优惠' }}</span>
            <h3>{{ voucher.title }}</h3>
            <p v-if="voucher.subTitle">{{ voucher.subTitle }}</p>
            <template v-if="Number(voucher.type) === 1">
              <p class="sale-time">{{ formatVoucherTime(voucher.beginTime) }} — {{ formatVoucherTime(voucher.endTime) }}（北京时间）</p>
              <p class="stock">剩余券名额 {{ voucher.stock ?? '待确认' }} <span>· 每人限购一张</span></p>
            </template>
            <button class="rules-link" @click="openVoucher(voucher)">查看使用规则</button>
          </div>
          <div class="voucher-price">
            <span class="face-value">可抵扣 ¥{{ money(voucher.actualValue) }}</span>
            <span class="sale-price"><small>券售价</small><strong><small>¥</small>{{ money(voucher.payValue) }}</strong></span>
            <el-button type="primary" :disabled="!availability(voucher).available" @click="openVoucher(voucher)">{{ availability(voucher).label }}</el-button>
          </div>
        </article>
      </section>
      <nav v-if="current > 1 || hasMore" class="pagination" aria-label="优惠券分页">
        <el-button :disabled="current <= 1 || loading" @click="loadOffers(current - 1)">上一页</el-button>
        <span>第 {{ current }} 页</span>
        <el-button :disabled="!hasMore || loading" @click="loadOffers(current + 1)">下一页</el-button>
      </nav>

      <el-dialog v-model="dialogVisible" title="购券确认" width="620px" :close-on-click-modal="!busy" :close-on-press-escape="!busy" :show-close="!busy" @closed="stopPolling">
        <template v-if="selected">
          <p class="subtle">{{ selected.shopName }}</p><h2 class="confirm-title">{{ selected.title }}</h2>
          <div class="confirm-prices"><span>券售价 <strong>¥{{ money(selected.payValue) }}</strong></span><span>可抵扣 ¥{{ money(selected.actualValue) }}</span></div>
          <h3 class="rules-title">使用规则</h3><p class="rules-content">{{ selected.rules || '使用规则尚未提供，暂不可购买。' }}</p>
          <p class="demo-note">仅演示优惠券购买及模拟支付，不产生真实扣款。购券不代表器材已预留，租期、押金和实际器材交易按门店规则办理。</p>
          <section v-if="order" class="order-panel" aria-live="polite">
            <strong>{{ verified ? voucherOrderStatus(order.status) : '正在确认订单状态' }}</strong>
            <p class="order-number">订单号：{{ order.orderId }}</p>
            <p v-if="Number(order.status) === 0">请求已受理，正在等待订单创建。请勿重复下单，可稍后刷新状态。</p>
            <p v-else-if="Number(order.status) === 2">模拟支付已完成，未发生真实扣款。</p>
            <p class="subtle">本次购券记录保存在当前账号的本机记录中。</p>
          </section>
          <p v-if="actionError" class="action-error" role="alert">{{ actionError }}</p>
        </template>
        <template #footer>
          <el-button :disabled="busy" @click="dialogVisible = false">关闭</el-button>
          <template v-if="order">
            <el-button :loading="checking" :disabled="busy" @click="refreshOrder()">刷新订单状态</el-button>
            <el-button type="primary" :disabled="!verified || Number(order.status) !== 1 || checking" :loading="paying" @click="pay">模拟支付</el-button>
          </template>
          <el-button v-else-if="selected" type="primary" :loading="ordering" :disabled="!availability(selected).available" @click="purchase">
            {{ userStore.isLoggedIn ? '确认购券' : '登录后购券' }}
          </el-button>
        </template>
      </el-dialog>
    </div>
  </main>
</template>

<script setup>
import { voucherApi } from '@/api/modules/voucher';
import { paymentApi } from '@/api/modules/payment';
import { useUserStore } from '@/store/user';
import { formatVoucherMoney as money, formatVoucherTime, voucherAvailability, voucherOrderStatus } from '@/utils/voucher';

const userStore = useUserStore();
const vouchers = ref([]);
const loading = ref(false);
const loadError = ref('');
const current = ref(1);
const hasMore = ref(false);
const brokenImages = reactive({});
const now = ref(Date.now());
const selected = ref(null);
const dialogVisible = ref(false);
const order = ref(null);
const verified = ref(false);
const ordering = ref(false);
const paying = ref(false);
const checking = ref(false);
const actionError = ref('');
const busy = computed(() => ordering.value || paying.value);
let clockTimer;
let pollTimer;
let context = 0;
let disposed = false;
const availability = (voucher) => voucherAvailability(voucher, now.value);
const cover = (voucher) => String(voucher.shopImages || '').split(',').map(s => s.trim()).find(Boolean) || '';

onMounted(() => {
  loadOffers(1);
  clockTimer = setInterval(() => { now.value = Date.now(); }, 1000);
});
onBeforeUnmount(() => { disposed = true; clearInterval(clockTimer); stopPolling(); });
watch(() => userStore.profile?.id, () => {
  stopPolling();
  dialogVisible.value = false;
  order.value = null;
  verified.value = false;
  ordering.value = false;
  paying.value = false;
  actionError.value = '';
});

async function loadOffers(page) {
  if (loading.value) return;
  loading.value = true;
  loadError.value = '';
  try {
    const data = await voucherApi.offers(page);
    if (!data || !Array.isArray(data.records)) throw new Error('优惠券数据格式异常，请稍后重试');
    vouchers.value = data.records;
    hasMore.value = data.hasMore === true;
    current.value = page;
  } catch (error) {
    loadError.value = error?.message || '优惠券加载失败，请稍后重试';
  } finally { loading.value = false; }
}

function stopPolling() {
  context += 1;
  clearTimeout(pollTimer);
  checking.value = false;
}

function openVoucher(voucher) {
  stopPolling();
  selected.value = voucher;
  actionError.value = '';
  verified.value = false;
  // 恢复当前账号尚未完成的订单，避免刷新或关闭弹窗后重复提交。
  order.value = userStore.orderHistory.find(item => String(item.voucherId) === String(voucher.id)
    && ([0, 1].includes(Number(item.status)) || (Number(voucher.type) === 1 && Number(item.status) !== 4))) || null;
  dialogVisible.value = true;
  if (order.value) refreshOrder();
}

async function purchase() {
  if (ordering.value || order.value) return;
  if (!userStore.isLoggedIn) {
    dialogVisible.value = false;
    userStore.openLoginDialog();
    return;
  }
  const state = voucherAvailability(selected.value);
  if (!state.available) { actionError.value = state.label; return; }
  const voucher = selected.value;
  const run = context;
  ordering.value = true;
  actionError.value = '';
  try {
    const seckill = Number(voucher.type) === 1;
    const id = await (seckill ? voucherApi.seckill(voucher.id) : voucherApi.buy(voucher.id));
    if (run !== context || disposed) return;
    order.value = {
      orderId: String(id), shopId: voucher.shopId, shopName: voucher.shopName,
      voucherId: voucher.id, voucherTitle: voucher.title, voucherType: voucher.type,
      asyncCreated: seckill, payAmount: money(voucher.payValue), actualAmount: money(voucher.actualValue),
      status: seckill ? 0 : 1
    };
    userStore.recordOrder(order.value);
    await refreshOrder(0, run);
    loadOffers(current.value);
  } catch (error) {
    if (run === context) actionError.value = error?.message || '购券请求失败，请检查订单记录后重试';
  } finally { if (run === context) ordering.value = false; }
}

async function refreshOrder(attempt = 0, run = context) {
  if (!order.value || checking.value || run !== context) return;
  clearTimeout(pollTimer);
  checking.value = true;
  actionError.value = '';
  const id = order.value.orderId;
  let pending = false;
  try {
    const status = Number(await paymentApi.status(id));
    if (run !== context || disposed) return;
    if (![0, 1, 2, 3, 4, 5, 6].includes(status)) throw new Error('订单状态暂未确认，请刷新后再支付');
    order.value = { ...order.value, status };
    userStore.updateOrder(id, { status });
    verified.value = true;
    pending = status === 0;
  } catch (error) {
    if (run === context) {
      verified.value = false;
      actionError.value = error?.message || '订单状态查询失败，请稍后刷新';
    }
  } finally {
    if (run === context) checking.value = false;
  }
  if (pending && attempt < 9 && run === context && !disposed) {
    pollTimer = setTimeout(() => refreshOrder(attempt + 1, run), 1000);
  }
}

async function pay() {
  if (paying.value || !verified.value || Number(order.value?.status) !== 1) return;
  paying.value = true;
  actionError.value = '';
  const run = context;
  try {
    await paymentApi.simulate(order.value.orderId);
    if (run !== context || disposed) return;
    // 只有服务端回查确认后才显示已支付。
    verified.value = false;
    await refreshOrder(0, run);
  } catch (error) {
    if (run === context) actionError.value = error?.message || '模拟支付失败，请刷新状态后重试';
  } finally { if (run === context) paying.value = false; }
}
</script>

<style scoped>
.offers-page { padding-top:36px; }
.offers-heading { display:flex; align-items:center; justify-content:space-between; gap:20px; margin-bottom:24px; }
.eyebrow { color:#888; font-size:11px; letter-spacing:2px; margin:0 0 12px; }
.offers-heading h1 { margin:0; font-size:30px; font-weight:650; }
.offers-heading p:last-child { color:#777; font-size:14px; margin-bottom:0; }
.demo-note { background:#f6f6f6; color:#666; padding:14px 18px; font-size:12px; line-height:1.8; border-radius:6px; }
.voucher-list { margin-top:28px; display:flex; flex-direction:column; gap:18px; }
.voucher-row { display:grid; grid-template-columns:minmax(240px, 1fr) minmax(260px, 1.6fr) 220px; align-items:center; gap:32px; padding:28px; border:1px solid #e6e6e6; border-radius:10px; }
.voucher-shop { display:flex; align-items:center; gap:18px; text-decoration:none; color:inherit; min-width:0; }
.voucher-shop img, .shop-placeholder { width:112px; height:112px; object-fit:cover; border-radius:6px; flex-shrink:0; }
.shop-placeholder { display:grid; place-items:center; background:#f1f1f1; font-size:12px; color:#888; }
.voucher-shop h2 { font-size:16px; line-height:1.6; margin:5px 0 8px; overflow-wrap:anywhere; }
.subtle { font-size:12px; color:#888; }
.shop-link { font-size:12px; color:#666; }
.voucher-info { min-width:0; }
.type-badge { display:inline-block; padding:4px 9px; font-size:11px; background:#f1f1f1; border-radius:4px; }
.voucher-info h3 { font-size:20px; margin:10px 0; line-height:1.5; overflow-wrap:anywhere; }
.voucher-info p { margin:7px 0; font-size:13px; color:#777; line-height:1.7; }
.voucher-info .sale-time { font-size:12px; }
.voucher-info .stock { color:#444; }
.stock span { color:#999; }
.rules-link { border:0; padding:4px 0; background:none; color:#555; font:inherit; font-size:12px; text-decoration:underline; text-underline-offset:4px; cursor:pointer; }
.voucher-price { display:flex; flex-direction:column; gap:10px; align-items:flex-end; padding-left:24px; border-left:1px dashed #ddd; }
.face-value { color:#777; font-size:13px; }
.sale-price { display:flex; align-items:baseline; gap:9px; }
.sale-price > small { font-size:12px; color:#777; }
.sale-price strong { font-size:32px; font-weight:650; letter-spacing:-1px; }
.sale-price strong small { font-size:17px; margin-right:3px; }
.voucher-price .el-button { min-width:156px; height:38px; margin-top:6px; }
.pagination { display:flex; justify-content:center; align-items:center; gap:20px; margin-top:32px; font-size:13px; }
.confirm-title { font-size:22px; line-height:1.5; margin:8px 0 20px; }
.confirm-prices { display:flex; flex-wrap:wrap; gap:24px; padding-bottom:18px; border-bottom:1px solid #eee; }
.confirm-prices strong { font-size:24px; color:#111; }
.rules-title { font-size:14px; margin-top:22px; }
.rules-content { white-space:pre-wrap; overflow-wrap:anywhere; font-size:13px; line-height:1.9; max-height:220px; overflow-y:auto; }
.order-panel { background:#f6f6f6; padding:16px; border-radius:6px; margin-top:18px; line-height:1.7; font-size:13px; }
.order-panel strong { color:#111; }
.order-number { overflow-wrap:anywhere; }
.action-error { color:#c03535; font-size:13px; line-height:1.7; }
@media(max-width:1050px) { .voucher-row { grid-template-columns:minmax(190px, 1fr) minmax(180px, 1.2fr) 190px; padding:20px; gap:20px; } .voucher-shop { flex-direction:column; align-items:flex-start; } .voucher-price { padding-left:18px; } }
@media(max-width:700px) { .offers-page { padding-top:24px; } .offers-heading h1 { font-size:26px; } .voucher-row { grid-template-columns:1fr; gap:18px; } .voucher-shop { flex-direction:row; align-items:center; } .voucher-shop img,.shop-placeholder { width:80px; height:80px; } .voucher-price { padding:18px 0 0; border-left:0; border-top:1px dashed #ddd; align-items:flex-start; } .voucher-price .el-button { width:100%; } }
</style>
