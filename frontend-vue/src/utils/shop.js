const categoryMeta = {
  人像: ['◉', '光影之间，记录人物'],
  动物: ['♧', '捕捉自然的灵动'],
  风景: ['△', '寻找山野与城市的光'],
  人文: ['▤', '街巷里的日常故事'],
  器材租借: ['▣', '探索适合你的拍摄装备']
};

export function normalizeCategory(type) {
  const name = type?.name || '未命名分类';
  const [icon, description] = categoryMeta[name] || ['◎', '浏览该分类的地点'];
  return { id: Number(type?.id), name, icon, description, rawIcon: type?.icon || '' };
}
export function normalizeCategories(list = []) { return list.map(normalizeCategory); }
export function normalizeShop(raw) {
  const distance = raw?.distance == null ? null : Number(raw.distance);
  return {
    id: Number(raw?.id), typeId: Number(raw?.typeId || 0),
    city: raw?.city ?? '', name: raw?.name || '未命名地点',
    price: Number(raw?.avgPrice || 0),
    distance: Number.isFinite(distance) && distance >= 0
      ? (distance < 1000 ? `${Math.round(distance)}m` : `${(distance / 1000).toFixed(1)}km`) : '',
    tags: [raw?.area].filter(Boolean),
    cover: String(raw?.images || '').split(',').map(item => item.trim()).find(Boolean) || '',
    address: raw?.address || '地址暂未提供',
    openHours: raw?.openHours || '',
    comments: [], raw
  };
}
export function mergeShopDetail(raw) { return normalizeShop(raw); }
