// Narrow native SVG types for vue-tsc 0.39's generated JSX, including its camel-case alias.
import type { SVGAttributes, HTMLAttributes, ReservedProps } from 'vue'
import type { RecordedMetricDomain } from '../sandbox/recordedMetricDomains'
type SandboxSvgAttributes = SVGAttributes & ReservedProps & { strokeWidth?: string | number }
declare global {
  namespace JSX {
    interface IntrinsicElements {
      // vue-tsc 0.39 also emits native JSX for Vue's presentation-only slot outlet.
      slot: HTMLAttributes & ReservedProps & { name?: string; group?: RecordedMetricDomain }
      div: HTMLAttributes & ReservedProps & { ariaLabel?: HTMLAttributes['aria-label']; ariaLive?: HTMLAttributes['aria-live']; dataLabel?: string; 'data-label'?: string }
      line: SandboxSvgAttributes
      polyline: SandboxSvgAttributes
      circle: SandboxSvgAttributes
      rect: SandboxSvgAttributes
    }
  }
}
