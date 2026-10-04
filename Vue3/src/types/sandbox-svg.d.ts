// Narrow native SVG types for vue-tsc 0.39's generated JSX, including its camel-case alias.
import type { SVGAttributes, HTMLAttributes, ReservedProps } from 'vue'
type SandboxSvgAttributes = SVGAttributes & ReservedProps & { strokeWidth?: string | number }
declare global {
  namespace JSX {
    interface IntrinsicElements {
      div: HTMLAttributes & ReservedProps & { ariaLabel?: HTMLAttributes['aria-label']; ariaLive?: HTMLAttributes['aria-live'] }
      line: SandboxSvgAttributes
      polyline: SandboxSvgAttributes
      circle: SandboxSvgAttributes
    }
  }
}
