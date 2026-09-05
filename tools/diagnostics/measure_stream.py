import argparse
import io
import json
import statistics
import threading
import time
import urllib.request
from pathlib import Path
from PIL import Image

p = argparse.ArgumentParser()
p.add_argument('label')
p.add_argument('--seconds', type=int, default=20)
p.add_argument('--url', required=True)
p.add_argument('--save-image', action='store_true')
args = p.parse_args()
root = Path.cwd() / 'diagnostics-output' / args.label
root.mkdir(parents=True, exist_ok=True)
base = args.url
stop = threading.Event()
samples = []

def sample_status():
    while not stop.is_set():
        try:
            with urllib.request.urlopen(base + '/api/status', timeout=3) as response:
                samples.append(json.load(response))
        except Exception as error:
            samples.append({'error': str(error)})
        stop.wait(1)

thread = threading.Thread(target=sample_status)
thread.start()
frames = 0
times = []
sizes = set()
total = 0
error = None
started = time.monotonic()
try:
    with urllib.request.urlopen(base + '/stream', timeout=5) as response:
        buf = b''
        while time.monotonic() - started < args.seconds:
            block = response.read1(65536)
            if not block:
                raise RuntimeError('Stream ended')
            buf += block
            while True:
                start = buf.find(b'\xff\xd8')
                if start < 0:
                    buf = buf[-1:]
                    break
                end = buf.find(b'\xff\xd9', start + 2)
                if end < 0:
                    buf = buf[start:]
                    break
                jpeg = buf[start:end+2]
                buf = buf[end+2:]
                with Image.open(io.BytesIO(jpeg)) as im:
                    im.load()
                    sizes.add(im.size)
                if frames == 0 and args.save_image:
                    (root / 'first-frame.jpg').write_bytes(jpeg)
                frames += 1
                total += len(jpeg)
                times.append(time.monotonic())
except Exception as exc:
    error = str(exc)
finally:
    stop.set()
    thread.join(5)
summary = {
    'url': base,
    'frames': frames, 'duration_seconds': time.monotonic()-started,
    'received_fps': (frames-1)/(times[-1]-times[0]) if frames > 1 else 0,
    'image_sizes': sorted(sizes), 'jpeg_bytes': total, 'error': error,
    'settings': samples[0] if samples else None,
}
for key in ['captureFps', 'encodedFps', 'encodeMs', 'replacedFrames']:
    values = [s[key] for s in samples if key in s]
    if values:
        summary[key] = {'mean': statistics.mean(values), 'min': min(values), 'max': max(values)}
(root/'status.json').write_text(json.dumps(samples, indent=2), encoding='utf-8')
(root/'summary.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')
print(json.dumps(summary, indent=2))
if error:
    raise SystemExit(1)
