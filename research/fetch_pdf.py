import sys, os, io, re, urllib.request, ssl
from pypdf import PdfReader

OUT = r"K:\moder\EyeOfHarmonyBuffer\research\pdfs"
os.makedirs(OUT, exist_ok=True)
ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE
HDR = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36",
       "Accept": "application/pdf,*/*"}

def grab(url, name):
    pdf = os.path.join(OUT, name + ".pdf")
    txt = os.path.join(OUT, name + ".txt")
    if os.path.exists(txt) and os.path.getsize(txt) > 500:
        print("CACHED", name, os.path.getsize(txt)); return
    try:
        req = urllib.request.Request(url, headers=HDR)
        data = urllib.request.urlopen(req, timeout=60, context=ctx).read()
    except Exception as e:
        print("DLFAIL", name, repr(e)[:200]); return
    if not data[:5].startswith(b"%PDF"):
        print("NOTPDF", name, data[:40]); return
    open(pdf, "wb").write(data)
    try:
        rd = PdfReader(pdf)
        parts = []
        for i, p in enumerate(rd.pages):
            parts.append("\n=== PAGE %d ===\n" % (i+1) + (p.extract_text() or ""))
        t = "".join(parts)
    except Exception as e:
        print("PDFFAIL", name, repr(e)[:200]); return
    open(txt, "w", encoding="utf-8", errors="replace").write(t)
    print("OK", name, len(data), "bytes ->", len(t), "chars", len(rd.pages), "pages")

if __name__ == "__main__":
    args = sys.argv[1:]
    for i in range(0, len(args), 2):
        grab(args[i], args[i+1])
