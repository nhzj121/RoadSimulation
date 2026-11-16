import { createHash } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const packDirectory = fileURLToPath(new URL('../src/main/resources/sandbox/shared-config/v1/', import.meta.url))
const defaultApi = 'http://localhost:8080/api/sandbox'
const fingerprint = /^[a-f0-9]{64}$/
const keyPattern = /^[a-z0-9][a-z0-9-]*$/

function requireThat(condition, message) {
  if (!condition) throw new Error(message)
}

function stableJson(value) {
  return JSON.stringify(value, (_key, item) => item && typeof item === 'object' && !Array.isArray(item)
    ? Object.fromEntries(Object.keys(item).sort().map(key => [key, item[key]])) : item)
}

export async function loadPack(directory = packDirectory) {
  const manifest = JSON.parse(await readFile(resolve(directory, 'pack.json'), 'utf8'))
  requireThat(manifest.artifactVersion === 'sandbox-shared-config/v1', 'Unsupported configuration pack')
  const groups = {}
  for (const [kind, keyField] of [['scenarios', 'scenarioKey'], ['runs', 'runSpecKey']]) {
    const entries = manifest[kind]
    requireThat(Array.isArray(entries) && entries.length > 0, `Missing ${kind} in the configuration pack`)
    const seen = new Set()
    groups[kind] = []
    for (const entry of entries) {
      const { key, sha256 } = entry
      requireThat(typeof key === 'string' && keyPattern.test(key) && !seen.has(key) && fingerprint.test(sha256),
        `Invalid or duplicate ${kind} entry: ${key}`)
      seen.add(key)
      // The pack hashes UTF-8 with LF so a Windows Git checkout can use CRLF.
      const content = (await readFile(resolve(directory, kind, `${key}.json`), 'utf8')).replace(/\r\n/g, '\n')
      requireThat(createHash('sha256').update(content).digest('hex') === sha256, `Configuration checksum mismatch: ${key}`)
      const definition = JSON.parse(content)
      requireThat(definition[keyField] === key, `Configuration key mismatch: ${key}`)
      groups[kind].push(definition)
    }
  }
  const scenarioKeys = new Set(groups.scenarios.map(definition => definition.scenarioKey))
  requireThat(groups.runs.every(definition => scenarioKeys.has(definition.scenario?.scenarioKey)), 'Run links to a scenario outside the pack')
  return { baseline: manifest.baseline, ...groups }
}

function localApi(value) {
  const url = new URL(value)
  requireThat(['http:', 'https:'].includes(url.protocol) && ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)
    && !url.username && !url.password && !url.search && !url.hash, 'The sandbox API must be a loopback URL')
  const base = url.href.replace(/\/$/, '')
  return async (path, body) => {
    const options = body === undefined ? {} : {
      method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Sandbox-Request': '1' }, body: JSON.stringify(body),
    }
    const response = await fetch(`${base}${path}`, { ...options, redirect: 'error', signal: AbortSignal.timeout(30000) })
    const result = await response.json().catch(() => null)
    if (!response.ok) {
      const failure = new Error(`${path}: ${result?.errorCode ?? `HTTP ${response.status}`}`)
      failure.code = result?.errorCode
      failure.status = response.status
      throw failure
    }
    requireThat(result !== null, `${path}: missing JSON response`)
    return result
  }
}

async function inspectDefinitions(api, kind, definitions, keyField, hashField) {
  const inspected = []
  for (const definition of definitions) {
    const key = definition[keyField]
    let draft
    try {
      draft = await api(`/${kind}/${key}/draft-view`)
    } catch (failure) {
      if (failure.status !== 404 || failure.code !== 'RECORD_NOT_FOUND') throw failure
    }
    requireThat(!draft || stableJson(draft.definition) === stableJson(definition), `EXISTING_DRAFT_DIFFERS ${kind}/${key}`)
    const compiled = await api(`/${kind}/compile`, definition)
    requireThat(fingerprint.test(compiled[hashField]), `Missing compilation fingerprint: ${kind}/${key}`)
    inspected.push({ key, definition, draft, hash: compiled[hashField] })
  }
  return inspected
}

async function publishDefinitions(api, kind, inspected, keyField, hashField) {
  const published = []
  for (const { key, definition, draft, hash } of inspected) {
    const saved = draft ?? await api(`/${kind}/guarded-draft`, { definition, expectedRowVersion: -1 })
    requireThat(Number.isSafeInteger(saved.rowVersion) && saved.rowVersion >= 0, `Invalid saved draft version: ${kind}/${key}`)
    const revision = await api(`/${kind}/${key}/guarded-publish`, {
      expectedRowVersion: saved.rowVersion, expectedDefinitionSha256: hash,
    })
    requireThat(revision[keyField] === key && Number.isSafeInteger(revision.revision) && revision.revision > 0
      && revision.fingerprints?.[hashField] === hash, `Publication fingerprint mismatch: ${kind}/${key}`)
    published.push(revision)
  }
  return published
}

export async function importPack(pack, api) {
  const baseline = await api('/baseline')
  const expected = pack.baseline
  requireThat(baseline.baselineId === expected.baselineId && baseline.effectiveBaseDataSha256 === expected.effectiveBaseDataSha256
    && baseline.fingerprints?.restorationPayloadSha256 === expected.restorationPayloadSha256
    && baseline.fingerprints?.simulationFactsSha256 === expected.simulationFactsSha256, 'BASELINE_MISMATCH')

  const scenes = await publishDefinitions(api, 'scenarios',
    await inspectDefinitions(api, 'scenarios', pack.scenarios, 'scenarioKey', 'scenarioDefinitionSha256'),
    'scenarioKey', 'scenarioDefinitionSha256')
  const byKey = new Map(scenes.map(scene => [scene.scenarioKey, scene]))
  const boundRuns = pack.runs.map(run => {
    const scene = byKey.get(run.scenario.scenarioKey)
    requireThat(fingerprint.test(scene.fingerprints.effectiveScenarioDataSha256), `Missing scenario data fingerprint: ${scene.scenarioKey}`)
    return { ...run, scenario: { scenarioKey: scene.scenarioKey, revision: scene.revision,
      scenarioDefinitionSha256: scene.fingerprints.scenarioDefinitionSha256,
      effectiveScenarioDataSha256: scene.fingerprints.effectiveScenarioDataSha256 } }
  })
  const runs = await publishDefinitions(api, 'runs',
    await inspectDefinitions(api, 'runs', boundRuns, 'runSpecKey', 'runSpecificationSha256'),
    'runSpecKey', 'runSpecificationSha256')
  return {
    scenarios: scenes.map(({ scenarioKey, revision, fingerprints }) => ({ scenarioKey, revision, ...fingerprints })),
    runs: runs.map(({ runSpecKey, revision, fingerprints }) => ({ runSpecKey, revision, runSpecificationSha256: fingerprints.runSpecificationSha256 })),
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {
    const args = process.argv.slice(2)
    requireThat(args.length <= 1 && (!args.length || args[0].startsWith('--api-base=')),
      `Usage: node scripts/import-sandbox-config.mjs [--api-base=${defaultApi}]`)
    const api = localApi(args.length ? args[0].slice('--api-base='.length) : defaultApi)
    console.log(JSON.stringify(await importPack(await loadPack(), api), null, 2))
  } catch (failure) {
    console.error(`Sandbox configuration import failed: ${failure.message}`)
    process.exitCode = 1
  }
}
