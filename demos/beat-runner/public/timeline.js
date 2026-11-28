export const SCENE_LABELS = {
  neutral: 'Neutral',
  drift: 'Drift',
  groove: 'Groove',
  surge: 'Surge',
}

function lastAtOrBefore(values, time, getTime) {
  let lo = 0
  let hi = values.length
  while (lo < hi) {
    const mid = Math.floor((lo + hi) / 2)
    if (getTime(values[mid]) <= time) lo = mid + 1
    else hi = mid
  }
  return values[lo - 1]
}

export function musicFrame(analysis, time) {
  const beat = lastAtOrBefore(analysis.beats, time, value => value)
  // Commit a newly inferred scene at the next beat when a grid is available.
  const sceneTime = analysis.beats.length ? (beat ?? 0) : time
  const style = lastAtOrBefore(analysis.styles, sceneTime, value => value.start)
  const pulse = beat === undefined ? 0 : Math.max(0, 1 - (time - beat) / 0.23) ** 2
  const confidence = style?.confidence ?? 0
  const theme = confidence >= 0.45 && Object.hasOwn(SCENE_LABELS, style.style) ? style.style : 'neutral'
  return { theme, pulse, confidence }
}
