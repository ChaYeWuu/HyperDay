from PIL import Image
img = Image.open(r"C:\Users\chaye\HyperMatter\Temp_screen.png").convert("RGB")
W, H = img.size
print("screen", W, H)

def is_cardish(px):
    r, g, b = px
    return (r > 240 and g > 240 and b > 240) or (r < 50 and g < 50 and b < 50)

def scan_col(x, y0, y1, label):
    runs = []
    prev = None
    for y in range(y0, y1):
        cur = is_cardish(img.getpixel((x, y)))
        if cur != prev:
            runs.append((y, cur))
            prev = cur
    print(label, runs[:20])

def scan_row(y, x0, x1, label):
    runs = []
    prev = None
    for x in range(x0, x1):
        cur = is_cardish(img.getpixel((x, y)))
        if cur != prev:
            runs.append((x, cur))
            prev = cur
    print(label, runs[:20])

# Card region: host [639,1176][1241,1828]
scan_col(940, 1150, 1850, "card col@940 (y,isCard):")
scan_col(700, 1150, 1850, "card col@700:")
scan_row(1300, 600, 1280, "card row@1300 (x,isCard):")
scan_row(1750, 600, 1280, "card row@1750:")

# List region: host [37,198][1241,850]
scan_col(640, 180, 880, "list col@640:")
scan_col(200, 180, 880, "list col@200:")
scan_row(400, 0, 1280, "list row@400:")
scan_row(750, 0, 1280, "list row@750:")

# Minimal region: find it — likely somewhere in the grid; dump whole screen coarse:
# print card-ish bounding boxes by sampling grid
for y in range(0, H, 8):
    row = [1 if is_cardish(img.getpixel((x, y))) else 0 for x in range(0, W, 8)]
    if sum(row) > 30:
        print("wide cardish row y=", y, "count=", sum(row))
