import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, resolve } from 'node:path'

const mobileRoot = resolve(import.meta.dirname, '..')
const repoRoot = resolve(mobileRoot, '..', '..')
const rootEnvPath = join(repoRoot, '.env.example')
const mobileEnvPath = join(mobileRoot, '.env.example')
const applicationPath = join(repoRoot, 'server', 'src', 'main', 'resources', 'application.yml')
const mobileSrcRoot = join(mobileRoot, 'src')

const rootEnv = parseEnvExample(rootEnvPath)
const mobileEnv = parseEnvExample(mobileEnvPath)
const backendVars = readPlaceholderVars(applicationPath)
const mobileVars = readMobileEnvVars(mobileSrcRoot)

const missingRoot = [...new Set([...backendVars, ...mobileVars])]
  .filter(name => !rootEnv.has(name))
  .sort()
const missingMobile = [...mobileVars]
  .filter(name => !mobileEnv.has(name))
  .sort()

if (missingRoot.length || missingMobile.length) {
  if (missingRoot.length) {
    console.error('Missing variables in root .env.example:')
    for (const name of missingRoot) {
      console.error(`- ${name}`)
    }
  }
  if (missingMobile.length) {
    console.error('Missing Vite variables in apps/mobile/.env.example:')
    for (const name of missingMobile) {
      console.error(`- ${name}`)
    }
  }
  process.exit(1)
}

console.log(
  `Checked ${backendVars.size} backend env vars and ${mobileVars.size} mobile env vars against examples.`
)

function parseEnvExample(file) {
  const content = readFileSync(file, 'utf8')
  return new Set(
    [...content.matchAll(/^([A-Z][A-Z0-9_]*)=/gm)].map(match => match[1])
  )
}

function readPlaceholderVars(file) {
  const content = readFileSync(file, 'utf8')
  return new Set(
    [...content.matchAll(/\$\{([A-Z][A-Z0-9_]*)(?::[^}]*)?}/g)].map(match => match[1])
  )
}

function readImportMetaVars(file) {
  const content = readFileSync(file, 'utf8')
  return new Set(
    [...content.matchAll(/(?:import\.meta\.env\.|readonly\s+)(VITE_[A-Z0-9_]+)/g)].map(match => match[1])
  )
}

function readMobileEnvVars(dir) {
  const vars = new Set()
  for (const file of walk(dir)) {
    if (!/\.(ts|vue)$/.test(file)) continue
    for (const name of readImportMetaVars(file)) {
      vars.add(name)
    }
  }
  return vars
}

function* walk(dir) {
  for (const entry of readdirSync(dir)) {
    const path = join(dir, entry)
    const stat = statSync(path)
    if (stat.isDirectory()) {
      yield* walk(path)
    } else {
      yield path
    }
  }
}
