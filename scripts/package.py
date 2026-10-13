from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import shutil
from html.parser import HTMLParser

project = Path(__file__).resolve().parent.parent
workspace = project.parent
release = project / 'release'
release.mkdir(exist_ok=True)
shutil.copy2(project / 'backend/target/costez-1.0.0.jar', release / 'costez.jar')
class Assets(HTMLParser):
    def handle_starttag(self, tag, attrs):
        for key, value in attrs:
            if key in ('src', 'href') and value and value.startswith('/assets/'):
                assert 'BOOT-INF/classes/static' + value in jar.namelist(), value
with ZipFile(release / 'costez.jar') as jar:
    Assets().feed(jar.read('BOOT-INF/classes/static/index.html').decode())
    assert 'BOOT-INF/classes/static/licenses/Instrument-Sans.txt' in jar.namelist()
for name in ['Start-Costez.cmd', 'Start-Costez.sh', 'README.md', 'VERIFICATION.md']:
    shutil.copy2(project / name, release / name)
with ZipFile(workspace / 'Costez-Ready-To-Run.zip', 'w', ZIP_DEFLATED) as archive:
    for file in release.iterdir():
        if file.is_file(): archive.write(file, file.name)
excluded = {'node_modules', 'target', 'dist', 'data', 'tools', 'release', 'static', '.git'}
with ZipFile(workspace / 'Costez-Full-Source.zip', 'w', ZIP_DEFLATED) as archive:
    for file in project.rglob('*'):
        relative = file.relative_to(project)
        if file.is_file() and not any(part in excluded for part in relative.parts) and file.name != '.env' and not file.name.endswith(('.log', '.tsbuildinfo')):
            archive.write(file, 'costez/' + relative.as_posix())
print('Created Costez-Ready-To-Run.zip and Costez-Full-Source.zip')
