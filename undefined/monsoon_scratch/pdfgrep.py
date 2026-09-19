
import sys, re, os
import pymupdf
def extract(path):
    d = pymupdf.open(path)
    txt = []
    for p in d:
        txt.append(p.get_text())
    return "\n".join(txt)
if __name__ == "__main__":
    mode = sys.argv[1]
    path = sys.argv[2]
    t = extract(path)
    t = t.replace("\u2013","-").replace("\u2212","-").replace("\u2014","-")
    t = re.sub(r"[ \t]+", " ", t)
    if mode == "grep":
        pats = sys.argv[3:]
        lines = t.split("\n")
        for i,l in enumerate(lines):
            for p in pats:
                if re.search(p, l, re.I):
                    ctx = " ".join(lines[max(0,i-1):i+2])
                    print(f"[{i}] {ctx[:600]}")
                    break
    else:
        print(t)
