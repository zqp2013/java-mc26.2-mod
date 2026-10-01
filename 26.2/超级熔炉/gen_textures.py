# 超级熔炉贴图生成:原版熔炉贴图 -> 各等级金属配色
# 石头/金属灰像素按亮度染成等级渐变,火焰等饱和像素(橙红)保留原色
import struct, zlib, os

def read_png(path):
    data = open(path, "rb").read()
    assert data[:8] == b"\x89PNG\r\n\x1a\n"
    pos = 8
    chunks = {}
    idat = b""
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos+4])[0]
        ctype = data[pos+4:pos+8]
        cdata = data[pos+8:pos+8+length]
        if ctype == b"IDAT":
            idat += cdata
        elif ctype != b"IEND":
            chunks.setdefault(ctype, cdata)
        pos += 12 + length
    w, h, depth, color = struct.unpack(">IIBB", chunks[b"IHDR"][:10])
    assert depth in (8, 4, 2, 1), f"depth {depth}"
    raw = zlib.decompress(idat)
    if color == 3:
        plte = chunks[b"PLTE"]
        palette = [(plte[i], plte[i+1], plte[i+2], 255) for i in range(0, len(plte), 3)]
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[color]
    # 反滤波
    stride = (w * channels * depth + 7) // 8
    out = []
    prev = bytearray(stride)
    p = 0
    for y in range(h):
        ftype = raw[p]; p += 1
        line = bytearray(raw[p:p+stride]); p += stride
        if ftype == 1:
            bpp = max(1, channels * depth // 8)
            for i in range(bpp, stride):
                line[i] = (line[i] + line[i-bpp]) & 0xFF
        elif ftype == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif ftype == 3:
            bpp = max(1, channels * depth // 8)
            for i in range(stride):
                left = line[i-bpp] if i >= bpp else 0
                line[i] = (line[i] + ((left + prev[i]) >> 1)) & 0xFF
        elif ftype == 4:
            bpp = max(1, channels * depth // 8)
            for i in range(stride):
                a = line[i-bpp] if i >= bpp else 0
                b = prev[i]
                c = prev[i-bpp] if i >= bpp else 0
                pa, pb, pc = abs(b-c), abs(a-c), abs(a+b-2*c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        out.append(bytes(line))
        prev = line
    pixels = []
    for y in range(h):
        row = out[y]
        for x in range(w):
            if color == 3:
                per = 8 // depth
                byte = row[x // per]
                shift = (per - 1 - x % per) * depth
                idx = (byte >> shift) & ((1 << depth) - 1)
                pixels.append(palette[idx])
            else:
                off = x * channels
                if color == 0:
                    g = row[off]; pixels.append((g, g, g, 255))
                elif color == 4:
                    g, a = row[off], row[off+1]; pixels.append((g, g, g, a))
                elif color == 2:
                    pixels.append((row[off], row[off+1], row[off+2], 255))
                elif color == 6:
                    pixels.append((row[off], row[off+1], row[off+2], row[off+3]))
    return w, h, pixels

def write_png(path, w, h, pixels):
    raw = b""
    stride = w * 4
    for y in range(h):
        row = bytearray()
        for x in range(w):
            r, g, b, a = pixels[y*w+x]
            row += bytes((r, g, b, a))
        raw += b"\x00" + row
    def chunk(ctype, data):
        c = ctype + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    open(path, "wb").write(png)

# 等级渐变:暗 -> 主色 -> 高光
TIERS = {
    "copper_furnace":    [(94,48,26), (196,110,58), (238,170,120)],
    "iron_furnace":      [(118,118,124), (199,199,205), (240,240,245)],
    "gold_furnace":      [(148,104,22), (240,196,42), (255,240,140)],
    "diamond_furnace":   [(28,108,103), (95,225,215), (190,255,250)],
    "netherite_furnace": [(34,25,39), (92,64,104), (162,131,172)],
}

def recolor(px, tier):
    dark, main, light = TIERS[tier]
    r, g, b, a = px
    if a == 0:
        return px
    mx, mn = max(r, g, b), min(r, g, b)
    # 灰色系(石头/金属)才染色;火焰等彩色像素保留
    if mx - mn > 42:
        return px
    lum = (r + g + b) / 3
    if lum <= 128:
        t = lum / 128
        col = tuple(round(dark[i] + (main[i] - dark[i]) * t) for i in range(3))
    else:
        t = (lum - 128) / 127
        col = tuple(round(main[i] + (light[i] - main[i]) * t) for i in range(3))
    return (col[0], col[1], col[2], a)

VAN = r"C:\Users\qpzhou\AppData\Local\Temp\furnaces\assets\minecraft\textures\block"
OUT = "src/main/resources/assets/superfurnace/textures/block"
os.makedirs(OUT, exist_ok=True)

for tier in TIERS:
    for src, dst in [("furnace_front", f"{tier}_front"),
                     ("furnace_front_on", f"{tier}_front_on"),
                     ("furnace_side", f"{tier}_side"),
                     ("furnace_top", f"{tier}_top")]:
        w, h, px = read_png(f"{VAN}/{src}.png")
        write_png(f"{OUT}/{dst}.png", w, h, [recolor(p, tier) for p in px])

print("textures:", len(os.listdir(OUT)))
