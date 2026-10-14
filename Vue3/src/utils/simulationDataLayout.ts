/** Phase 6：只定义展示断点和浮窗像素边界，不接触车辆、任务或地图实例。 */
export function simulationDataLayout(width: number, height: number) {
  const safeWidth = Number.isFinite(width) && width > 0 ? width : 1920;
  const safeHeight = Number.isFinite(height) && height > 0 ? height : 1080;
  const mode = safeWidth >= 1600 ? 'wide' : safeWidth >= 1280 ? 'desktop' : safeWidth >= 900 ? 'compact' : 'mobile';
  const drawerWidth = Math.min(380, Math.max(240, safeWidth - 24));
  return { mode, leftDrawer: safeWidth < 1280, rightDrawer: safeWidth < 900,
    leftWidth: mode === 'wide' ? 380 : mode === 'desktop' ? 330 : drawerWidth,
    rightWidth: mode === 'wide' ? 400 : mode === 'desktop' ? 350 : mode === 'compact' ? 340 : drawerWidth,
    collapsedBottomDefault: safeWidth < 900 || safeHeight < 600 };
}
export function clampSimulationFloatingPosition(left: number, top: number, regionWidth: number,
  regionHeight: number, windowWidth: number, windowHeight: number) {
  // 地图小于浮窗时将起点限制在可见区域；CSS 同时限制浮窗自身宽高。
  const width = Math.max(0, regionWidth), height = Math.max(0, regionHeight);
  const minLeft = Math.min(8, width), minTop = Math.min(8, height);
  const maxLeft = Math.max(minLeft, width - windowWidth - 8), maxTop = Math.max(minTop, height - windowHeight - 8);
  return { left: Math.min(Math.max(minLeft, left), maxLeft), top: Math.min(Math.max(minTop, top), maxTop) };
}
