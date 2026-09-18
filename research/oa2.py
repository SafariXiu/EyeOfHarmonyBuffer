import sys, os, json, re, urllib.request, urllib.parse, ssl, time
from pypdf import PdfReader

OUT = r"K:\moder\EyeOfHarmonyBuffer\research\pdfs"
os.makedirs(OUT, exist_ok=True)
ctx = ssl.create_default_context(); ctx.check_hostname=False; ctx.verify_mode=ssl.CERT_NONE
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

def get(url, timeout=70, accept="application/pdf,*/*"):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": accept})
    return urllib.request.urlopen(req, timeout=timeout, context=ctx).read()

def jget(url):
    return json.loads(get(url, accept="application/json").decode("utf-8", "replace"))

def cands(doi):
    out = []
    # Semantic Scholar
    try:
        j = jget("https://api.semanticscholar.org/graph/v1/paper/DOI:%s?fields=title,year,openAccessPdf,externalIds" % urllib.parse.quote(doi))
        if j.get("openAccessPdf"): out.append(("s2", j["openAccessPdf"]["url"]))
        print("   S2:", (j.get("title") or "")[:70], "|", j.get("year"), "| oa:", bool(j.get("openAccessPdf")))
    except Exception as e: print("   s2fail", repr(e)[:90])
    # Unpaywall
    try:
        j = jget("https://api.unpaywall.org/v2/%s?email=research@example.org" % urllib.parse.quote(doi))
        b = j.get("best_oa_location") or {}
        if b.get("url_for_pdf"): out.append(("upw-pdf", b["url_for_pdf"]))
        if b.get("url"): out.append(("upw-land", b["url"]))
        for l in (j.get("oa_locations") or []):
            if l.get("url_for_pdf"): out.append(("upw2", l["url_for_pdf"]))
        print("   UPW:", j.get("is_oa"), (b.get("url_for_pdf") or b.get("url") or "-")[:110])
    except Exception as e: print("   upwfail", repr(e)[:90])
    # OpenAlex
    try:
        j = jget("https://api.openalex.org/works/doi:" + urllib.parse.quote(doi))
        for l in (j.get("locations") or []):
            if l.get("pdf_url"): out.append(("oalex", l["pdf_url"]))
        print("   OALEX:", j.get("title","")[:60], j.get("publication_year"))
    except Exception as e: print("   oalexfail", repr(e)[:90])
    return out

def extract(data, name):
    pdf = os.path.join(OUT, name + ".pdf"); txt = os.path.join(OUT, name + ".txt")
    if not data[:5].startswith(b"%PDF"): return False
    open(pdf,"wb").write(data)
    try:
        rd = PdfReader(pdf)
        t = "".join(("\n=== PAGE %d ===\n"%(i+1)) + (p.extract_text() or "") for i,p in enumerate(rd.pages))
    except Exception as e:
        print("   PDFFAIL", repr(e)[:100]); return False
    open(txt,"w",encoding="utf-8",errors="replace").write(t)
    print("   OK", name, len(data), "->", len(t), "chars", len(rd.pages),"pg"); return True

def html_dump(data, name):
    h = os.path.join(OUT, name + ".html")
    open(h,"wb").write(data); print("   HTML", name, len(data)); return True

def wayback(url):
    try:
        j = jget("https://archive.org/wayback/available?url=" + urllib.parse.quote(url, safe=""))
        s = (j.get("archived_snapshots") or {}).get("closest")
        return s.get("url") if s else None
    except Exception as e:
        print("   wbfail", repr(e)[:80]); return None

def run(doi, name, extra_land=None):
    print("==", name, doi)
    lst = cands(doi)
    if extra_land:
        for u in extra_land: lst.append(("given", u))
    seen=set()
    for tag,u in lst:
        if not u or u in seen: continue
        seen.add(u)
        try: d = get(u)
        except Exception as e:
            print("   dlfail[%s] %s %s" % (tag, u[:90], repr(e)[:70])); 
            w = wayback(u)
            if w:
                try:
                    d = get(w); 
                    if extract(d, name) or html_dump(d, name): return
                except Exception as e2: print("   wbfail2", repr(e2)[:70])
            continue
        if extract(d, name): return
        if b"<html" in d[:2000].lower():
            w = wayback(u)
            if w:
                try:
                    if extract(get(w), name): return
                except Exception as e2: print("   wbpdf-fail", repr(e2)[:70])
    print("   NONE", name)

if __name__ == "__main__":
    a = sys.argv[1:]
    for i in range(0,len(a),2):
        run(a[i], a[i+1])
