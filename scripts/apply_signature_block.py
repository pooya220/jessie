#!/usr/bin/env python3
"""Apply a precomputed public APK v2 signature block. Private key stays offline."""
from pathlib import Path
import base64, struct, sys, zipfile
MAGIC=b'APK Sig Block 42'
def n32(b,i):return struct.unpack_from('<I',b,i)[0]
def n64(b,i):return struct.unpack_from('<Q',b,i)[0]
def apply(src,patch,dest):
    b=Path(src).read_bytes()
    block=base64.b64decode(Path(patch).read_text().strip(),validate=True)
    if len(block)<64 or block[-16:]!=MAGIC or n64(block,0)+8!=len(block) or n64(block,len(block)-24)+8!=len(block):
        raise ValueError('Invalid permanent signature block')
    e=b.rfind(b'PK\x05\x06')
    if e<0 or e+22!=len(b):raise ValueError('Malformed APK ZIP EOCD')
    cd=n32(b,e+16)
    if b[cd-16:cd]!=MAGIC:raise ValueError('Original signed block absent')
    old=n64(b,cd-24)+8
    start=cd-old
    if start<0 or n64(b,start)+8!=old:raise ValueError('Invalid old block')
    rest=bytearray(b[cd:])
    struct.pack_into('<I',rest,e-cd+16,start+len(block))
    result=b[:start]+block+rest
    Path(dest).parent.mkdir(parents=True,exist_ok=True)
    Path(dest).write_bytes(result)
    with zipfile.ZipFile(dest) as z:
        if z.testzip() is not None:raise ValueError('ZIP CRC invalid')
    print('Signed candidate APK:',len(result),'bytes')
if __name__=='__main__':
    if len(sys.argv)!=4:raise SystemExit('Usage: input.apk signed-block.b64 output.apk')
    apply(*sys.argv[1:])
