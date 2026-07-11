import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative, resolve } from 'node:path'

const mobileRoot = resolve(import.meta.dirname, '..')
const repoRoot = resolve(mobileRoot, '..', '..')
const serverRoot = join(repoRoot, 'server', 'src', 'main', 'java')
const clientPath = join(mobileRoot, 'src', 'api', 'client.ts')

const backendCodes = new Set()
const backendPatterns = [
  /new BusinessException\([^,]+,\s*"([A-Z][A-Z0-9_]+)"/g,
  /ApiResponse\.fail\("([A-Z][A-Z0-9_]+)"\)/g,
  /new ApiResponse<[^>]*>\(\s*false,[\s\S]*?,\s*"([A-Z][A-Z0-9_]+)"\s*\)/g
]

for (const file of walk(serverRoot)) {
  if (!file.endsWith('.java')) continue
  const content = readFileSync(file, 'utf8')
  for (const pattern of backendPatterns) {
    for (const match of content.matchAll(pattern)) {
      backendCodes.add(match[1])
    }
  }
}

const client = readFileSync(clientPath, 'utf8')
const messagesBlock = client.match(/const ERROR_MESSAGES:[\s\S]+?\n}/)?.[0] || ''
const frontendCodes = new Set(
  [...messagesBlock.matchAll(/^\s+([A-Z][A-Z0-9_]+):/gm)].map(match => match[1])
)

const missing = [...backendCodes]
  .filter(code => !frontendCodes.has(code))
  .sort()

if (missing.length) {
  console.error('Missing mobile error messages for backend codes:')
  for (const code of missing) {
    console.error(`- ${code}`)
  }
  process.exit(1)
}

console.log(`Checked ${backendCodes.size} backend error codes against mobile messages.`)

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
