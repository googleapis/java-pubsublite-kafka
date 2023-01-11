import base64
import datetime
import google.auth
import google.auth.transport.urllib3
import http.server
import json
import urllib3

_credentials, _project = google.auth.default(scopes=['https://www.googleapis.com/auth/cloud-platform'])
_http_client = urllib3.PoolManager()


def valid_credentials():
    if not _credentials.valid:
        _credentials.refresh(
            google.auth.transport.urllib3.Request(_http_client))
    return _credentials


_HEADER = json.dumps(dict(typ='JWT', alg='GOOG_TOKEN'))


def get_jwt(creds):
    return json.dumps(dict(exp=creds.expiry.timestamp(),
                           iat=datetime.datetime.utcnow().timestamp(),
                           scope='pubsub',
                           sub='unused'))


def b64_encode(source):
    return base64.urlsafe_b64encode(source.encode('utf-8')).decode('utf-8')


def get_kafka_access_token(creds):
    return '.'.join([b64_encode(_HEADER), b64_encode(get_jwt(creds)),
                     b64_encode(creds.token)])


def build_message():
    creds = valid_credentials()
    expiry_seconds = (creds.expiry - datetime.datetime.utcnow()).total_seconds()
    return json.dumps(
        dict(access_token=get_kafka_access_token(creds), token_type='bearer',
             expires_in=expiry_seconds))


class AuthHandler(http.server.BaseHTTPRequestHandler):
    def _handle(self):
        self.send_response(200)
        self.send_header('Content-type', 'text/plain')
        self.end_headers()
        self.wfile.write(build_message().encode('utf-8'))

    def do_GET(self):
        self._handle()

    def do_POST(self):
        self._handle()


def run_server():
    server_address = ('localhost', 14293)
    server = http.server.ThreadingHTTPServer(server_address, AuthHandler)
    print("Serving on localhost:14293. This is not accessible outside of the "
          "current machine.")
    server.serve_forever()


if __name__ == '__main__':
    run_server()
