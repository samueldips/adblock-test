#!/usr/bin/env python3
"""Generate a simple launcher icon PNG (192x192, xxxhdpi) with stdlib only."""
import zlib, struct, binascii, os

W = H = 192
BG = (11, 18, 32)        # #0B1220
TEAL = (77, 208, 166)    # #4DD0A6
DARK = (22, 32, 47)      # #16202F

def circle(px, cx, cy, r, color):
    for y in range(max(0, cy - r), min(H, cy + r + 1)):
        for x in range(max(0, cx - r), min(W, cx + r + 1)):
            if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                px[y][x] = color

def thick_line(px, x0, y0, x1, y1, w, color):
    # simple thick line via stamping circles along the segment
    import math
    length = math.hypot(x1 - x0, y1 - y0)
    steps = int(length) + 1
    for i in range(steps + 1):
        t = i / steps
        circle(px, int(x0 + (x1 - x0) * t), int(y0 + (y1 - y0) * t), w // 2, color)

px = [[BG for _ in range(W)] for _ in range(H)]
circle(px, 96, 96, 88, DARK)          # dark disc
circle(px, 96, 96, 80, TEAL)          # teal ring area
circle(px, 96, 96, 62, DARK)          # inner dark disc
# checkmark in teal
thick_line(px, 66, 98, 90, 122, 16, TEAL)
thick_line(px, 90, 122, 130, 70, 16, TEAL)

raw = b''.join(b'\x00' + b''.join(struct.pack('BBB', *p) for p in row) for row in px)

def chunk(typ, data):
    c = struct.pack('>I', len(data)) + typ + data
    return c + struct.pack('>I', binascii.crc32(typ + data) & 0xffffffff)

png = (b'\x89PNG\r\n\x1a\n'
       + chunk(b'IHDR', struct.pack('>IIBBBBB', W, H, 8, 2, 0, 0, 0))
       + chunk(b'IDAT', zlib.compress(raw, 9))
       + chunk(b'IEND', b''))

out = os.path.expanduser('~/workspace/adblock-test-lab/app/res/mipmap-xxxhdpi/ic_launcher.png')
os.makedirs(os.path.dirname(out), exist_ok=True)
with open(out, 'wb') as f:
    f.write(png)
print('wrote', out, len(png), 'bytes')
