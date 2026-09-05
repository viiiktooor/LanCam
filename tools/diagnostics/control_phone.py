import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ADB = [os.environ.get('ADB', 'adb'), '-s', os.environ['LANCAM_DEVICE']]

def adb(*args):
    return subprocess.check_output(ADB + list(args))

def tree():
    raw = adb('exec-out', 'uiautomator', 'dump', '/dev/tty').decode('utf-8')
    xml = raw[raw.index('<?xml'):raw.index('</hierarchy>') + len('</hierarchy>')]
    return ET.fromstring(xml)

def tap(text):
    nodes = [n for n in tree().iter('node') if n.get('text') == text and n.get('package') == 'com.example.lancam']
    if len(nodes) != 1:
        raise RuntimeError('Expected one visible app control: ' + text)
    x1,y1,x2,y2 = map(int, re.findall(r'\d+', nodes[0].get('bounds')))
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))

if sys.argv[1] == 'tap':
    for text in sys.argv[2:]:
        tap(text)
elif sys.argv[1] == 'dump':
    for n in tree().iter('node'):
        if n.get('text'):
            print(n.get('text'), n.get('bounds'))
elif sys.argv[1] == 'screenshot':
    Path(sys.argv[2]).write_bytes(adb('exec-out', 'screencap', '-p'))
