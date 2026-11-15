#!/usr/bin/env python3
"""Pinned Android 9 Canvas test runtime only; not included in the TV APK."""
from pathlib import Path
from urllib.request import urlopen
from hashlib import sha256
from zipfile import ZipFile

root = Path(__file__).resolve().parent / 'build'
items = [
 ('weather-sdk/android-all-instrumented-9-robolectric-4913185-2-i7.jar',
  'https://repo.maven.apache.org/maven2/org/robolectric/android-all-instrumented/9-robolectric-4913185-2-i7/android-all-instrumented-9-robolectric-4913185-2-i7.jar',
  'dbb1c88987b54cef3132695a1b4d2f4851ba06286a6fa7d15772f98fd1f0cdf7', None),
 ('weather-aar/monitor.aar', 'https://dl.google.com/dl/android/maven2/androidx/test/monitor/1.8.0/monitor-1.8.0.aar',
  '56cb7496a06d9f2dca7d3ff76c50a8a30bd18e00a24a3b267d5a31437b278e67', 'monitor.jar'),
 ('weather-aar/espresso.aar', 'https://dl.google.com/dl/android/maven2/androidx/test/espresso/espresso-idling-resource/3.7.0/espresso-idling-resource-3.7.0.aar',
  '5ff62326b49c308c1d060466ae3cf4aa0e3deaf9295f077a6886048dda3e9b14', 'espresso.jar')
]
for relative, url, digest, classes in items:
    target = root / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    if not target.exists():
        temporary = target.with_suffix(target.suffix + '.part')
        with urlopen(url, timeout=45) as response, temporary.open('wb') as out:
            while True:
                block = response.read(1024 * 1024)
                if not block:
                    break
                out.write(block)
        temporary.replace(target)
    if sha256(target.read_bytes()).hexdigest() != digest:
        raise SystemExit('Checksum mismatch: ' + target.name)
    if classes:
        with ZipFile(target) as archive:
            (target.parent / classes).write_bytes(archive.read('classes.jar'))
    print('Verified', target.name)
