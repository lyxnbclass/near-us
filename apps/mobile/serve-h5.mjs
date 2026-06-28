import { createServer } from 'node:http'
import { readFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { extname, join, normalize } from 'node:path'

const root = 'D:/code/aicode-yy/apps/mobile/dist/build/h5'
const port = 5188

const types = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.svg': 'image/svg+xml'
}

createServer(async (req, res) => {
  try {
    const url = new URL(req.url || '/', `http://127.0.0.1:${port}`)
    let relative = decodeURIComponent(url.pathname).replace(/^\/+/, '') || 'index.html'
    let filePath = normalize(join(root, relative))
    if (!filePath.startsWith(normalize(root)) || !existsSync(filePath)) {
      filePath = join(root, 'index.html')
    }
    const body = await readFile(filePath)
    res.writeHead(200, { 'Content-Type': types[extname(filePath)] || 'application/octet-stream' })
    res.end(body)
  } catch {
    res.writeHead(500)
    res.end('server error')
  }
}).listen(port, '127.0.0.1', () => {
  console.log(`Serving ${root} at http://127.0.0.1:${port}/`)
})
