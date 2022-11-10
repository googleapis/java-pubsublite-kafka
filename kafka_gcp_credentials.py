import datetime
import google.auth
import google.auth.transport.urllib3
import http.server
import json
import urllib3

_credentials, _project = google.auth.default()
_http_client = urllib3.PoolManager()


def valid_credentials():
    if not _credentials.valid:
        _credentials.refresh(
            google.auth.transport.urllib3.Request(_http_client))
    return _credentials


def build_message():
    creds = valid_credentials()
    expiry_seconds = 3600
    if creds.expiry:
        expiry_seconds = (
            creds.expiry - datetime.datetime.utcnow()).total_seconds()
    return json.dumps(dict(access_token=creds.token, token_type='bearer',
                           expires_in=expiry_seconds))


class AuthHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200)
        self.send_header('Content-type', 'text/plain')
        self.end_headers()
        self.wfile.write(build_message().encode('utf-8'))


def run_server():
    server_address = ('localhost', 14293)
    server = http.server.ThreadingHTTPServer(server_address, AuthHandler)
    print("Serving on localhost:14293. This is not accessible outside of the "
          "current machine.")
    server.serve_forever()


if __name__ == '__main__':
    run_server()
