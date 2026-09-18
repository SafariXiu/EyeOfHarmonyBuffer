import sys, os, re, json, urllib.request, urllib.parse, ssl, time, html as htmllib

OUT = r"K:\moder\EyeOfHarmonyBuffer\research\pdfs"
os.makedirs(OUT, exist_ok=True)
ctx = ssl.create_default_context(); ctx.check_hostname=False; ctx.verify_mode=ssl.CERT_NONE
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

def get(url, timeout=90, accept="text/html,*/*"):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": accept})
    return urllib.request.urlopen(req, timeout=timeout, context=ctx).read()

def totext(b):
    s = b.decode("utf-8", "replace")
    s = re.sub(r"(?is)<(script|style|noscript|svg)[^>]*>.*?</\1>", " ", s)
    # keep math alt text
    s = re.sub(r'(?is)<img[^>]*alt="([^"]*)"[^>]*>', r" [IMG:\1] ", s)
    s = re.sub(r"(?is)<br\s*/?>", "\n", s)
    s = re.sub(r"(?is)</(p|div|li|h[1-6]|tr|table|section)>", "\n", s)
    s = re.sub(r"(?is)<[^>]+>", " ", s)
    s = htmllib.unescape(s)
    s = re.sub(r"[ \t\xa0]+", " ", s)
    s = re.sub(r"\n\s*\n\s*\n+", "\n\n", s)
    return s

def cdx(url, limit=8):
    u = "http://web.archive.org/cdx/search/cdx?url=%s&output=json&limit=%d&filter=statuscode:200&collapse=timestamp:6" % (urllib.parse.quote(url, safe=""), limit)
    try:
        j = json.loads(get(u, accept="application/json").decode("utf-8","replace"))
        return [r[1] for r in j[1:]]
    except Exception as e:
        print("   cdxfail", repr(e)[:90]); return []

def wb(url, name):
    out = os.path.join(OUT, name + ".txt")
    if os.path.exists(out) and os.path.getsize(out) > 2000:
        print("CACHED", name, os.path.getsize(out)); return
    for ts in cdx(url) or []:
        for variant in ("https://web.archive.org/web/%sid_/%s" % (ts, url),):
            try:
                b = get(variant)
            except Exception as e:
                print("   fail", variant[:80], repr(e)[:70]); continue
            if b[:5].startswith(b"%PDF"):
                open(os.path.join(OUT, name+".pdf"),"wb").write(b)
                try:
                    from pypdf import PdfReader
                    rd = PdfReader(os.path.join(OUT,name+".pdf"))
                    t = "".join(("\n=== PAGE %d ===\n"%(i+1))+(p.extract_text() or "") for i,p in enumerate(rd.pages))
                except Exception as e: print("   pdfx fail", repr(e)[:80]); continue
            else:
                t = totext(b)
            if len(t) > 3000:
                open(out,"w",encoding="utf-8",errors="replace").write(t)
                print("OK", name, ts, len(t), "chars"); return
            else:
                print("   short", name, ts, len(t))
    print("NONE", name)

if __name__ == "__main__":
    a = sys.argv[1:]
    for i in range(0,len(a),2):
        print("==", a[i+1])
        wb(a[i], a[i+1]); time.sleep(1)
