/** Temporary presentation switches only. Never use these to gate simulation or API calls. */
export const temporaryDisplaySettings = Object.freeze({
  hideRealtimeEvaluationErrors: true,
  showWeatherControls: false,
  showRandomEventControls: false,
});

/** Retain the diagnostic source; only select the cards rendered on screen. */
export function evaluationWarningsForDisplay<T extends { level: 'error' | 'warning' }>(
  warnings: readonly T[],
  hideErrors: boolean = temporaryDisplaySettings.hideRealtimeEvaluationErrors,
): T[] {
  return warnings.filter(warning => !hideErrors || warning.level !== 'error');
}
