import { vehicleStatusPresentation } from './vehicleStatusPresentation'

interface VehicleIconMeta {
  vehicleId?: number | string
  vrp?: boolean
  carriedCount?: number
}

// Create vehicle icons with status-aware styling.
export const createVehicleIcon = (size = 32, status = 'IDLE', color: string | null = null, meta: VehicleIconMeta = {}) => {
  const el = document.createElement('div');
  if (meta.vehicleId !== undefined && meta.vehicleId !== null) el.dataset.vehicleId = String(meta.vehicleId);
  el.style.width = `${size}px`;
  el.style.height = `${size}px`;
  el.style.borderRadius = '50%';
  el.style.display = 'flex';
  el.style.alignItems = 'center';
  el.style.justifyContent = 'center';
  el.style.boxShadow = '0 2px 8px rgba(0,0,0,0.3)';
  el.style.border = '2px solid white';
  el.style.position = 'relative';

  // 状态颜色映射
  // 设置背景颜色
  const bgColor = color || vehicleStatusPresentation(status).color || '#ff7f50';
  el.style.background = bgColor;
  el.style.color = '#fff';

  // 根据状态生成不同的SVG图标
  let svgContent = '';
  const iconSize = Math.round(size * 0.6);

  switch (status) {
    case 'ORDER_DRIVING':
      // 空车图标（灰色或蓝色）
      svgContent = `
        <svg width="${iconSize}" height="${iconSize}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <rect x="1" y="6" width="15" height="6" rx="1"></rect>
          <path d="M16 6h4l2 3v3h-6"></path>
          <circle cx="5.5" cy="16.5" r="1.5"></circle>
          <circle cx="18.5" cy="16.5" r="1.5"></circle>
        </svg>`;
      break;

    case 'TRANSPORT_DRIVING':
      // 载货车图标（显示货物）
      svgContent = `
        <svg width="${iconSize}" height="${iconSize}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <rect x="1" y="6" width="15" height="6" rx="1"></rect>
          <path d="M16 6h4l2 3v3h-6"></path>
          <circle cx="5.5" cy="16.5" r="1.5"></circle>
          <circle cx="18.5" cy="16.5" r="1.5"></circle>
          <rect x="4" y="4" width="8" height="2" rx="0.5" fill="#ffeb3b"></rect>
        </svg>`;
      break;

    case 'LOADING':
      // 装载中图标（带加载动画效果）
      svgContent = `
        <svg width="${iconSize}" height="${iconSize}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <rect x="1" y="6" width="15" height="6" rx="1"></rect>
          <path d="M16 6h4l2 3v3h-6"></path>
          <circle cx="5.5" cy="16.5" r="1.5"></circle>
          <circle cx="18.5" cy="16.5" r="1.5"></circle>
          <path d="M8 10v-4" stroke-dasharray="2,2"></path>
        </svg>`;
      break;

    case 'UNLOADING':
      // 卸货中图标
      svgContent = `
        <svg width="${iconSize}" height="${iconSize}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <rect x="1" y="6" width="15" height="6" rx="1"></rect>
          <path d="M16 6h4l2 3v3h-6"></path>
          <circle cx="5.5" cy="16.5" r="1.5"></circle>
          <circle cx="18.5" cy="16.5" r="1.5"></circle>
          <path d="M12 10v4" stroke-dasharray="2,2"></path>
        </svg>`;
      break;

    default:
      // 默认车辆图标
      svgContent = `
        <svg width="${iconSize}" height="${iconSize}" viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg">
          <path d="M3 13v-6h11v6H3zm13 0h3l2 3v3h-3a2 2 0 0 1-2-2v-4zM6 18a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3zm10 0a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3z"/>
        </svg>`;
  }

  el.innerHTML = svgContent;

  if (meta?.vrp && Number(meta.carriedCount) > 0) {
    const badge = document.createElement('span');
    badge.textContent = String(Math.min(99, Number(meta.carriedCount)));
    badge.style.position = 'absolute';
    badge.style.top = '-7px';
    badge.style.right = '-7px';
    badge.style.minWidth = '16px';
    badge.style.height = '16px';
    badge.style.padding = '0 4px';
    badge.style.borderRadius = '999px';
    badge.style.background = '#111827';
    badge.style.border = '2px solid #fff';
    badge.style.color = '#fff';
    badge.style.fontSize = '10px';
    badge.style.lineHeight = '14px';
    badge.style.fontWeight = '700';
    badge.style.textAlign = 'center';
    badge.style.boxSizing = 'border-box';
    el.appendChild(badge);
  }

  return el;
};

interface VehicleMarkerAppearance extends VehicleIconMeta {
  status: string
  color?: string | null
  selected?: boolean
}

const appearanceByMarker = new WeakMap<object, { icon: string; title: string }>()

function iconKey(appearance: VehicleMarkerAppearance) {
  return JSON.stringify([appearance.status, appearance.color, appearance.vehicleId,
    appearance.vrp ? Number(appearance.carriedCount) || 0 : 0, Boolean(appearance.selected)])
}

function markerContent(appearance: VehicleMarkerAppearance) {
  const element = createVehicleIcon(32, appearance.status, appearance.color, appearance)
  if (appearance.selected) element.style.outline = '2px solid #b64214'
  return element
}

export function createVehicleMapMarker<Marker extends object>(
  amap: { Marker: new (options: any) => Marker },
  appearance: VehicleMarkerAppearance,
  options: { title: string; [option: string]: unknown },
): Marker {
  const marker = new amap.Marker({ ...options, content: markerContent(appearance) })
  appearanceByMarker.set(marker, { icon: iconKey(appearance), title: options.title })
  return marker
}

export function updateVehicleMapMarker(
  marker: { setContent(element: HTMLElement): void; setTitle(title: string): void },
  appearance: VehicleMarkerAppearance & { title: string },
) {
  const previous = appearanceByMarker.get(marker)
  const icon = iconKey(appearance)
  if (previous?.icon !== icon) marker.setContent(markerContent(appearance))
  if (previous?.title !== appearance.title) marker.setTitle(appearance.title)
  appearanceByMarker.set(marker, { icon, title: appearance.title })
}
