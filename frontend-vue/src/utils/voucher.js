export const formatVoucherMoney = (value) => value == null ? '—' : (Number(value) / 100).toFixed(2);

// 后端 LocalDateTime 无时区，活动时间统一按北京时间解释。
export function voucherTime(value) {
  if (!value) return NaN;
  const text = String(value).replace(' ', 'T');
  return Date.parse(/(?:Z|[+-]\d{2}:\d{2})$/i.test(text) ? text : `${text}+08:00`);
}

export function voucherAvailability(voucher, now = Date.now()) {
  if (Number(voucher.status) !== 1) return { available: false, label: '已下架' };
  if (![0, 1].includes(Number(voucher.type))) return { available: false, label: '类型待确认' };
  if (voucher.payValue == null || !Number.isFinite(Number(voucher.payValue)) || Number(voucher.payValue) <= 0
    || voucher.actualValue == null || !Number.isFinite(Number(voucher.actualValue)) || Number(voucher.actualValue) <= 0) {
    return { available: false, label: '金额待完善' };
  }
  if (!voucher.rules?.trim()) return { available: false, label: '规则待完善' };
  if (Number(voucher.type) === 1) {
    const begin = voucherTime(voucher.beginTime);
    const end = voucherTime(voucher.endTime);
    if (!Number.isFinite(begin) || !Number.isFinite(end) || end <= begin || voucher.stock == null) {
      return { available: false, label: '活动信息待完善' };
    }
    if (now < begin) return { available: false, label: '未开始' };
    if (now >= end) return { available: false, label: '已结束' };
    if (!Number.isFinite(Number(voucher.stock)) || Number(voucher.stock) <= 0) return { available: false, label: '已抢光' };
    return { available: true, label: '抢购优惠券' };
  }
  return { available: true, label: '购买优惠券' };
}

export function formatVoucherTime(value) {
  const time = voucherTime(value);
  if (!Number.isFinite(time)) return '未提供';
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hour12: false
  }).format(time);
}

export function voucherOrderStatus(status) {
  return ({ 0: '订单创建中', 1: '待支付', 2: '已支付', 3: '已核销', 4: '已取消', 5: '退款中', 6: '已退款' })[status] || '状态待确认';
}
