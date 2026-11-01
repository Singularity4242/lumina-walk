<template>
  <div class="page-shell">
    <div class="page-container detail-page" v-if="detail">
      <div class="detail-top-bar">
        <el-button circle @click="router.back()">
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <div class="right-actions">
          <el-button round @click="toggleFavorite">
            {{ userStore.isFavorite(detail.id) ? '已收藏' : '收藏' }}
          </el-button>
        </div>
      </div>

      <section class="hero card-panel">
        <el-carousel v-if="carouselImages.length" height="300px">
          <el-carousel-item v-for="image in carouselImages" :key="image">
            <img class="hero-image" :src="image" :alt="detail.name" />
          </el-carousel-item>
        </el-carousel>
        <div v-else class="detail-placeholder">LUMINA WALK · 暂无地点图片</div>

        <div class="hero-info">
          <div>
            <h1>{{ detail.name }}</h1>
            <div class="score-row">
              <span v-if="detail.price > 0">参考均价 ¥{{ detail.price }}</span>
              <span v-if="detail.distance">{{ detail.distance }}</span>
            </div>
          </div>

          <div class="meta-list">
            <div v-if="detail.city?.trim()"><strong>城市：</strong>{{ detail.city }}</div>
            <div><strong>地址：</strong>{{ detail.address }}</div>
            <div v-if="detail.openHours"><strong>开放 / 营业时间：</strong>{{ detail.openHours }}</div>
          </div>

          <div class="action-row">
            <el-button @click="toggleFavorite">收藏到本机</el-button>
            <el-button v-if="canNavigate" @click="openNavigation">导航</el-button>
          </div>
        </div>
      </section>

      <section class="content-grid">

        <article class="card-panel offer-panel">
          <div class="section-title">
            <h2>器材优惠券</h2>
            <p>线上购买抵扣券，到店按规则使用。</p>
          </div>
          <p class="purchase-notice">平台仅提供购券。器材租赁或购买、余款、租期、押金及核销由门店线下办理；购券不代表预留器材。当前支付为模拟演示，尚未提供线上核销功能。</p>
          <div class="offer-list">
            <div v-for="voucher in vouchers" :key="voucher.id" class="offer-item hover-lift" :class="{ disabled: !canPurchaseVoucher(voucher) }">
              <div class="offer-main">
                <div class="offer-head">
                  <strong>{{ voucher.title }}</strong>
                  <div class="offer-badges">
                    <span class="voucher-badge" :class="isSeckillVoucher(voucher) ? 'is-seckill' : 'is-normal'">
                      {{ isSeckillVoucher(voucher) ? '限时抢购' : '直接购买' }}
                    </span>
                    <span class="voucher-badge is-light" :class="voucherStatusClass(voucher)">
                      {{ getVoucherStatusText(voucher) }}
                    </span>
                  </div>
                </div>
                <span>支付 ¥{{ formatMoney(voucher.payValue) }} / 抵扣 ¥{{ formatMoney(voucher.actualValue) }}</span>
                <span>{{ formatVoucherMeta(voucher) }}</span>
                <span v-if="voucher.subTitle">{{ voucher.subTitle }}</span>
                <details class="voucher-rules">
                  <summary>查看使用规则</summary>
                  <p>{{ voucher.rules || '使用规则暂未提供，请先确认适用范围。' }}</p>
                </details>
              </div>
              <div class="offer-actions">
                <el-button
                  size="small"
                  type="primary"
                  :loading="ordering && activeVoucherId === voucher.id"
                  :disabled="ordering || !canPurchaseVoucher(voucher)"
                  @click="submitVoucherOrder(voucher)"
                >
                  {{ getVoucherActionText(voucher) }}
                </el-button>
              </div>
            </div>
            <div v-if="!vouchers.length" class="empty-block">此地点暂无可购买的器材券</div>
          </div>

          <div v-if="latestOrderId" class="payment-box">
            <strong>当前订单：{{ latestOrderId }}</strong>
            <span class="order-status-text">{{ orderReady ? '订单已创建，可继续模拟支付' : '订单创建中，请稍候后再支付' }}</span>
            <div class="payment-actions">
              <el-button size="small" :loading="checkingOrder" @click="checkPaymentStatus">查状态</el-button>
              <el-button size="small" :disabled="!orderReady" :loading="paying" @click="simulatePayment">模拟支付</el-button>
              <el-button size="small" :disabled="!orderReady" :loading="paying" @click="openPayUrl('alipay')">支付宝（演示）</el-button>
              <el-button size="small" :disabled="!orderReady" :loading="paying" @click="openPayUrl('wechat')">微信（演示）</el-button>
            </div>
          </div>
        </article>
      </section>

      <section class="card-panel comments-panel">
        <div class="section-title">
          <h2>摄影笔记</h2>
          <p>{{ commentTip }}</p>
        </div>
        <article v-for="note in detail.comments" :key="note.id" class="place-note">
          <h3>{{ note.title }}</h3><span class="tag-chip">{{ note.user }}</span><p>{{ note.content }}</p>
          <div class="note-images"><img v-for="image in note.images" :key="image" :src="image" :alt="note.title" loading="lazy" /></div>
        </article>
        <p v-if="!detail.comments.length" class="empty-block">当前热门笔记中未找到此地点的关联内容。</p>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ElMessage } from 'element-plus';
import { ArrowLeft } from '@element-plus/icons-vue';
import { shopApi } from '@/api/modules/shop';
import { voucherApi } from '@/api/modules/voucher';
import { paymentApi } from '@/api/modules/payment';
import { blogApi } from '@/api/modules/blog';
import { useUserStore } from '@/store/user';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const detail = ref(null);
const canNavigate = computed(() => Boolean(detail.value?.address) && !detail.value.address.includes('虚构演示地点'));
const vouchers = ref([]);
const latestOrderId = ref(null);
const latestOrderMeta = ref(null);
const orderReady = ref(false);
const ordering = ref(false);
const paying = ref(false);
const checkingOrder = ref(false);
const activeVoucherId = ref(null);

const carouselImages = computed(() => {
  if (!detail.value) return [];
  return [detail.value.cover, ...detail.value.comments.flatMap((comment) => comment.images || [])]
    .filter(Boolean)
    .slice(0, 3);
});

const commentTip = '从热门笔记中展示与此地点关联的内容，暂不代表全部笔记。';

onMounted(loadDetail);

async function loadDetail() {
  try {
    const shopDetail = await shopApi.getDetail(route.params.id);
    const [voucherList, shopBlogs] = await Promise.all([
      voucherApi.listByShop(route.params.id).catch(() => []),
      loadShopBlogs(route.params.id)
    ]);

    detail.value = mergeDetail(shopDetail, shopBlogs);
    vouchers.value = voucherList;
    const latestOrder = findLatestOrder(detail.value.id);
    latestOrderId.value = latestOrder?.orderId || null;
    latestOrderMeta.value = latestOrder || null;
    if (latestOrderId.value) {
      const shouldWaitAsync = isAsyncPendingOrder(latestOrderMeta.value);
      orderReady.value = !shouldWaitAsync;
      if (shouldWaitAsync) {
        await ensureOrderReady(latestOrderId.value, false);
      }
    }
  } catch (error) {
    ElMessage.error(error?.message || '加载地点详情失败');
  }
}

async function loadShopBlogs(shopId) {
  const results = [];
  for (let page = 1; page <= 3 && results.length < 3; page += 1) {
    const list = await blogApi.getHot(page).catch(() => []);
    const matched = (list || []).filter((item) => Number(item.shopId) === Number(shopId));
    results.push(...matched);
  }
  const uniqueMap = new Map();
  results.forEach((item) => uniqueMap.set(item.id, item));
  return [...uniqueMap.values()].slice(0, 3);
}

function mergeDetail(baseDetail, shopBlogs) {
  return { ...baseDetail, comments: shopBlogs.map(blog => ({
    id: blog.id, user: blog.name || '摄影爱好者', title: blog.title || '摄影笔记',
    content: blog.content || '', images: parseImages(blog.images)
  })) };
}

function parseImages(images) {
  return String(images || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
    .slice(0, 3);
}



function findLatestOrder(shopId) {
  const orders = userStore.orderHistory.filter((item) => Number(item.shopId) === Number(shopId));
  return orders[0] || null;
}

function toggleFavorite() {
  const isFavorite = userStore.toggleFavorite(detail.value.id);
  ElMessage.success(isFavorite ? '已加入收藏' : '已取消收藏');
}

function openNavigation() {
  const keyword = encodeURIComponent(`${detail.value.name} ${detail.value.address}`);
  window.open(`https://uri.amap.com/search?keyword=${keyword}`, '_blank');
}



function isSeckillVoucher(voucher) {
  return Number(voucher?.type) === 1;
}

function toTime(value) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.getTime();
}

function getVoucherStatus(voucher) {
  if (voucher?.status != null && Number(voucher.status) !== 1) {
    return 'offline';
  }
  if (isSeckillVoucher(voucher)) {
    const now = Date.now();
    const beginTime = toTime(voucher.beginTime);
    const endTime = toTime(voucher.endTime);
    if (beginTime && now < beginTime) {
      return 'upcoming';
    }
    if (endTime && now > endTime) {
      return 'expired';
    }
    if (Number(voucher.stock ?? 0) <= 0) {
      return 'soldout';
    }
  }
  return 'available';
}

function getVoucherStatusText(voucher) {
  switch (getVoucherStatus(voucher)) {
    case 'offline': return '已下架';
    case 'upcoming': return '未开始';
    case 'expired': return '已结束';
    case 'soldout': return '已抢光';
    default: return '可购买';
  }
}

function voucherStatusClass(voucher) {
  return `is-${getVoucherStatus(voucher)}`;
}

function canPurchaseVoucher(voucher) {
  return getVoucherStatus(voucher) === 'available';
}

function getVoucherActionText(voucher) {
  if (!canPurchaseVoucher(voucher)) {
    return getVoucherStatusText(voucher);
  }
  return isSeckillVoucher(voucher) ? '抢券' : '立即购买';
}

function formatVoucherMeta(voucher) {
  if (isSeckillVoucher(voucher)) {
    const begin = voucher.beginTime ? String(voucher.beginTime).replace('T', ' ').slice(0, 16) : '开始时间未提供';
    return `限时抢购 ｜ 剩余券名额 ${voucher.stock ?? '未提供'} ｜ ${begin} 开始`;
  }
  return '直接购买 ｜ 即时购买 ｜ 下单后可直接支付';
}

function isAsyncPendingOrder(order) {
  if (!order) return false;
  const asyncCreated = order.asyncCreated === true;
  const seckillType = Number(order.voucherType) === 1;
  return (asyncCreated || seckillType) && [0, 1].includes(Number(order.status ?? 1));
}

async function submitVoucherOrder(voucher) {
  if (!canPurchaseVoucher(voucher)) {
    ElMessage.warning(`${getVoucherStatusText(voucher)}，暂不可下单`);
    return;
  }
  activeVoucherId.value = voucher.id;
  try {
    if (isSeckillVoucher(voucher)) {
      await seckillVoucher(voucher);
      return;
    }
    await buyVoucher(voucher);
  } finally {
    activeVoucherId.value = null;
  }
}

async function buyVoucher(voucher) {
  if (!userStore.isLoggedIn) {
    userStore.openLoginDialog();
    return;
  }
  ordering.value = true;
  try {
    latestOrderId.value = String(await voucherApi.buy(voucher.id));
    latestOrderMeta.value = {
      orderId: latestOrderId.value,
      shopId: detail.value.id,
      voucherId: voucher.id,
      voucherType: voucher.type,
      asyncCreated: false,
      status: 1
    };
    orderReady.value = true;
    userStore.recordOrder({
      orderId: latestOrderId.value,
      shopId: detail.value.id,
      shopName: detail.value.name,
      voucherId: voucher.id,
      voucherTitle: voucher.title,
      voucherType: voucher.type,
      asyncCreated: false,
      payAmount: formatMoney(voucher.payValue),
      actualAmount: formatMoney(voucher.actualValue),
      status: 1
    });
    ElMessage.success(`直接购买下单成功，订单ID：${latestOrderId.value}，可以直接支付`);
  } catch (error) {
    ElMessage.error(error?.message || '购券失败');
  } finally {
    ordering.value = false;
  }
}

async function seckillVoucher(voucher) {
  if (!userStore.isLoggedIn) {
    userStore.openLoginDialog();
    return;
  }
  ordering.value = true;
  try {
    latestOrderId.value = String(await voucherApi.seckill(voucher.id));
    latestOrderMeta.value = {
      orderId: latestOrderId.value,
      shopId: detail.value.id,
      voucherId: voucher.id,
      voucherType: voucher.type,
      asyncCreated: true,
      status: 1
    };
    orderReady.value = false;
    userStore.recordOrder({
      orderId: latestOrderId.value,
      shopId: detail.value.id,
      shopName: detail.value.name,
      voucherId: voucher.id,
      voucherTitle: voucher.title,
      voucherType: voucher.type,
      asyncCreated: true,
      payAmount: formatMoney(voucher.payValue),
      actualAmount: formatMoney(voucher.actualValue),
      status: 1
    });
    ElMessage.success(`抢券成功，订单ID：${latestOrderId.value}，正在等待订单落库`);
    await ensureOrderReady(latestOrderId.value, true);
  } catch (error) {
    ElMessage.error(error?.message || '抢券失败');
  } finally {
    ordering.value = false;
  }
}

async function ensureOrderReady(orderId, notifyWhenReady = false) {
  checkingOrder.value = true;
  try {
    const targetOrderId = String(orderId);
    for (let attempt = 0; attempt < 10; attempt += 1) {
      try {
        const status = await paymentApi.status(targetOrderId);
        if (Number(status) === 0) {
          await wait(800);
          continue;
        }
        orderReady.value = true;
        userStore.updateOrder(targetOrderId, { status: Number(status) });
        if (latestOrderMeta.value && String(latestOrderMeta.value.orderId) === targetOrderId) {
          latestOrderMeta.value = {
            ...latestOrderMeta.value,
            status: Number(status)
          };
        }
        if (notifyWhenReady) {
          ElMessage.success('订单已创建完成，可以继续支付');
        }
        return true;
      } catch (error) {
        if (!String(error?.message || '').includes('订单不存在') && !String(error?.message || '').includes('订单创建中')) {
          throw error;
        }
        await wait(800);
      }
    }
    ElMessage.warning('订单正在排队创建，请稍后再点击支付');
    return false;
  } finally {
    checkingOrder.value = false;
  }
}

async function checkPaymentStatus() {
  if (!latestOrderId.value) return;
  try {
    const ready = isAsyncPendingOrder(latestOrderMeta.value)
      ? await ensureOrderReady(latestOrderId.value, false)
      : true;
    if (!ready) return;
    const status = await paymentApi.status(latestOrderId.value);
    userStore.updateOrder(latestOrderId.value, { status: Number(status) });
    if (latestOrderMeta.value) {
      latestOrderMeta.value = {
        ...latestOrderMeta.value,
        status: Number(status)
      };
    }
    ElMessage.success(`当前订单状态：${formatOrderStatus(status)}`);
  } catch (error) {
    ElMessage.error(error?.message || '查询状态失败');
  }
}

async function simulatePayment() {
  if (!latestOrderId.value) return;
  paying.value = true;
  try {
    const ready = isAsyncPendingOrder(latestOrderMeta.value)
      ? await ensureOrderReady(latestOrderId.value, false)
      : true;
    if (!ready) return;
    const result = await paymentApi.simulate(latestOrderId.value);
    userStore.updateOrder(latestOrderId.value, { status: 2, payType: 1 });
    orderReady.value = true;
    if (latestOrderMeta.value) {
      latestOrderMeta.value = {
        ...latestOrderMeta.value,
        status: 2,
        payType: 1
      };
    }
    ElMessage.success(typeof result === 'string' ? result : '模拟支付成功');
  } catch (error) {
    ElMessage.error(error?.message || '模拟支付失败');
  } finally {
    paying.value = false;
  }
}

async function openPayUrl(type) {
  if (!latestOrderId.value) return;
  paying.value = true;
  try {
    const ready = isAsyncPendingOrder(latestOrderMeta.value)
      ? await ensureOrderReady(latestOrderId.value, false)
      : true;
    if (!ready) return;
    const result = type === 'alipay'
      ? await paymentApi.alipay(latestOrderId.value)
      : await paymentApi.wechat(latestOrderId.value);
    userStore.updateOrder(latestOrderId.value, { payType: type === 'alipay' ? 2 : 3 });
    if (latestOrderMeta.value) {
      latestOrderMeta.value = {
        ...latestOrderMeta.value,
        payType: type === 'alipay' ? 2 : 3
      };
    }
    ElMessage.success(result?.payUrl ? `支付链接：${result.payUrl}` : '支付链接已生成');
  } catch (error) {
    ElMessage.error(error?.message || '创建支付单失败');
  } finally {
    paying.value = false;
  }
}

function formatMoney(value) {
  const amount = Number(value || 0);
  return (amount / 100).toFixed(2);
}

function formatOrderStatus(status) {
  switch (Number(status)) {
    case 0: return '订单创建中';
    case 1: return '待支付';
    case 2: return '已支付';
    case 3: return '已核销';
    case 4: return '已取消';
    case 5: return '退款中';
    case 6: return '已退款';
    default: return '未知状态';
  }
}

function wait(duration) {
  return new Promise((resolve) => setTimeout(resolve, duration));
}
</script>

<style scoped lang="scss">
.detail-page { padding-top: 18px; }
.detail-top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}
.right-actions { display: flex; gap: 10px; }
.hero {
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  gap: 18px;
  padding: 18px;
}
.hero-image {
  width: 100%;
  height: 300px;
  object-fit: cover;
  border-radius: 14px;
  background: #fff4eb;
}
.hero-info {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 14px;
}
.hero-info h1 { margin: 0; font-size: 30px; }
.score-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 10px;
  color: #666;
}
.meta-list {
  display: grid;
  gap: 12px;
  line-height: 1.8;
  color: #555;
}
.action-row { display: flex; flex-wrap: wrap; gap: 10px; }
.content-grid {
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: 16px;
  margin-top: 18px;
}
.dishes-panel,
.offer-panel,
.comments-panel { padding: 18px; }
.dish-list { display: grid; gap: 14px; }
.dish-item {
  display: grid;
  grid-template-columns: 110px 1fr;
  gap: 14px;
  padding: 12px;
  border-radius: 12px;
  background: #fffaf7;
}
.dish-item img {
  width: 100%;
  height: 88px;
  object-fit: cover;
  border-radius: 10px;
}
.dish-item p { margin: 8px 0; color: #666; }
.dish-item span { color: var(--dp-primary); font-weight: 700; }
.offer-list { display: grid; gap: 12px; }
.offer-item {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
  padding: 14px;
  border-radius: 12px;
  color: #8a4a14;
  background: #fff5ec;
}
.offer-main { display: grid; gap: 6px; }
.offer-main span { font-size: 13px; color: #8a4a14; }
.offer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}
.offer-badges {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.voucher-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}
.voucher-badge.is-seckill {
  color: #fff;
  background: linear-gradient(135deg, #ff6700, #ff8a3d);
}
.voucher-badge.is-normal {
  color: #ff6700;
  background: #fff1e7;
}
.voucher-badge.is-light {
  color: #8a4a14;
  background: #fde6d5;
}
.voucher-badge.is-available {
  color: #1f8f55;
  background: #e9f8ef;
}
.voucher-badge.is-upcoming {
  color: #9a6b00;
  background: #fff4d9;
}
.voucher-badge.is-expired,
.voucher-badge.is-offline,
.voucher-badge.is-soldout {
  color: #999;
  background: #f1f1f1;
}
.offer-item.disabled {
  opacity: 0.82;
  background: #faf7f4;
}
.offer-actions,
.payment-actions { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.payment-box {
  display: grid;
  gap: 12px;
  margin-top: 14px;
  padding: 14px;
  border-radius: 12px;
  background: #f8f8f8;
}
.order-status-text { color: #999; font-size: 13px; }
.empty-block { color: #999; }
.comments-panel { margin-top: 18px; }
@media (max-width: 767px) {
  .detail-top-bar,
  .hero,
  .content-grid,
  .offer-item { grid-template-columns: 1fr; flex-direction: column; }
  .detail-top-bar { align-items: flex-start; gap: 10px; }
  .right-actions { width: 100%; flex-wrap: wrap; }
  .hero-info h1 { font-size: 24px; }
  .dish-item { grid-template-columns: 1fr; }
}
</style>


<style scoped>
.content-grid { grid-template-columns:1fr; }
.detail-placeholder { min-height:300px; display:grid; place-items:center; background:#e9eee3; color:#607260; border-radius:14px; letter-spacing:2px; }
.place-note { padding:20px 0; border-top:1px solid var(--dp-border); }
.place-note p,.voucher-rules { white-space:pre-wrap; line-height:1.9; overflow-wrap:anywhere; }
.note-images { display:flex; flex-wrap:wrap; gap:12px; }
.note-images img { width:180px; max-width:100%; height:140px; object-fit:cover; border-radius:12px; }
.offer-item { background:#f0f3eb; color:#355940; }
.offer-main span { color:#496250; }
.voucher-badge.is-seckill { background:var(--dp-primary); }
.voucher-badge.is-normal { color:var(--dp-primary); background:var(--dp-primary-light); }
.voucher-rules { color:#496250; font-size:13px; }
.voucher-rules summary { width:fit-content; cursor:pointer; font-weight:600; }
.voucher-rules p { margin:10px 0 0; }
@media(max-width:600px) { .hero { grid-template-columns:1fr; } .offer-item { grid-template-columns:1fr; } }
</style>
