#!/usr/bin/env python3
"""Assemble local release artifacts; never publish."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tarfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = dict(line.split('=', 1) for line in (ROOT / 'gradle.properties').read_text().splitlines() if '=' in line)['version']
TARGET = ROOT / 'build' / 'release'
TARGET.mkdir(parents=True, exist_ok=True)
for old in TARGET.iterdir():
    if old.is_file():
        old.unlink()
shutil.copyfile(ROOT / 'cli/build/starter-cli-runner.jar', TARGET / f'starter-cli-{VERSION}.jar')
with tarfile.open(TARGET / f'starter-server-{VERSION}.tar.gz', 'w:gz') as archive:
    archive.add(ROOT / 'server/build/quarkus-app', arcname='quarkus-app')
for module, source in [('cli', 'starter-cli-runner-cyclonedx.json'), ('server', 'quarkus-run-cyclonedx.json')]:
    path = ROOT / module / 'build' / source
    sbom = json.loads(path.read_text())
    if sbom.get('bomFormat') != 'CycloneDX' or not sbom.get('components'):
        raise SystemExit(f'Invalid {module} SBOM')
    shutil.copyfile(path, TARGET / f'{module}.cdx.json')
source_roots = ['README.md', 'AGENTS.md', 'gradle.properties', 'settings.gradle.kts', 'build.gradle.kts',
                'gradlew', 'gradlew.bat', 'gradle', 'mise.toml', 'mise.lock', '.editorconfig', '.gitignore',
                '.dockerignore', '.gitattributes', '.env.example', '.github', 'core', 'cli', 'server', 'infra', 'deploy', 'scripts', 'docs']
files = []
for name in source_roots:
    path = ROOT / name
    files.extend([path] if path.is_file() else [p for p in path.rglob('*') if p.is_file()
                                              and not {'build', '__pycache__'}.intersection(p.relative_to(ROOT).parts)])
digest = hashlib.sha256()
for path in sorted(files):
    digest.update(str(path.relative_to(ROOT)).encode() + b'\0' + path.read_bytes() + b'\0')
commit = os.environ.get('GITHUB_SHA') or os.environ.get('SOURCE_COMMIT')
if not commit:
    result = subprocess.run(['git', 'rev-parse', 'HEAD'], cwd=ROOT, capture_output=True, text=True)
    commit = result.stdout.strip() if result.returncode == 0 else None
(TARGET / 'SOURCE.json').write_text(json.dumps({'version': VERSION, 'gitCommit': commit,
                                               'sourceTreeSha256': digest.hexdigest()}, indent=2) + '\n')
checksums = ''.join(hashlib.sha256(path.read_bytes()).hexdigest() + '  ' + path.name + '\n'
                    for path in sorted(TARGET.iterdir()) if path.is_file() and path.name != 'SHA256SUMS')
(TARGET / 'SHA256SUMS').write_text(checksums)
for line in checksums.splitlines():
    expected, name = line.split('  ', 1)
    if hashlib.sha256((TARGET / name).read_bytes()).hexdigest() != expected:
        raise SystemExit('Checksum verification failed')
print(f'Local release artifacts and verified SHA-256 checksums: {TARGET}')
