import { readdirSync } from 'node:fs'
import { join, resolve } from 'node:path'

const mobileRoot = resolve(import.meta.dirname, '..')
const repoRoot = resolve(mobileRoot, '..', '..')
const migrationRoot = join(repoRoot, 'server', 'src', 'main', 'resources', 'db', 'migration')
const migrationPattern = /^V(\d+)__[a-z0-9_]+\.sql$/

const files = readdirSync(migrationRoot)
  .filter(name => name.endsWith('.sql'))
  .sort((a, b) => versionOf(a) - versionOf(b))

const invalidNames = files.filter(name => !migrationPattern.test(name))
const versions = new Map()
for (const file of files) {
  const version = versionOf(file)
  if (!versions.has(version)) {
    versions.set(version, [])
  }
  versions.get(version).push(file)
}

const duplicateVersions = [...versions.entries()]
  .filter(([, names]) => names.length > 1)
const missingVersions = []
const maxVersion = Math.max(0, ...versions.keys())
for (let version = 1; version <= maxVersion; version += 1) {
  if (!versions.has(version)) {
    missingVersions.push(version)
  }
}

if (invalidNames.length || duplicateVersions.length || missingVersions.length) {
  if (invalidNames.length) {
    console.error('Invalid Flyway migration names:')
    for (const name of invalidNames) {
      console.error(`- ${name}`)
    }
  }
  if (duplicateVersions.length) {
    console.error('Duplicate Flyway migration versions:')
    for (const [version, names] of duplicateVersions) {
      console.error(`- V${version}: ${names.join(', ')}`)
    }
  }
  if (missingVersions.length) {
    console.error(`Missing Flyway migration versions: ${missingVersions.map(version => `V${version}`).join(', ')}`)
  }
  process.exit(1)
}

console.log(`Checked ${files.length} Flyway migrations through V${maxVersion}.`)

function versionOf(name) {
  const match = name.match(/^V(\d+)__/)
  return match ? Number(match[1]) : Number.MAX_SAFE_INTEGER
}
