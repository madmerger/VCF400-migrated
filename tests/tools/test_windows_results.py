import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

from openpyxl import load_workbook
import pytest

from case_results import fingerprint


@pytest.mark.parametrize('windows_state', ['current', 'missing', 'stale', 'legacy', 'failed'])
def test_windows_columns_preserve_api_web_results_and_fingerprints(tmp_path, windows_state):
    root = Path(__file__).resolve().parents[2]
    for relative in ['tests/tools/gen_cases.py', 'tests/tools/case_results.py',
                     'spec/tools/gen_spec.py', 'spec/tools/spec_data.json']:
        target = tmp_path / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(root / relative, target)

    def generate():
        subprocess.run([sys.executable, str(tmp_path / 'tests/tools/gen_cases.py')],
                       cwd=tmp_path, env={**os.environ, 'PYTHONUTF8': '1'}, check=True,
                       capture_output=True, encoding='utf-8')
        book = load_workbook(tmp_path / 'tests/VCF400_e2e_cases.xlsx')
        rows = list(book.active.values)
        book.close()
        return rows

    empty = generate()
    assert empty[1][17:] == (None, None, '未実施', None, None)
    document = json.loads((tmp_path / 'tests/cases.json').read_text(encoding='utf-8'))
    hashes = [fingerprint(c, document['baseline']) for c in document['cases']]
    case = document['cases'][0]
    for platform, relative in [
        ('API', 'backend/test-results/api_e2e_results.json'),
        ('Web', 'frontend/test-results/web_e2e_results.json'),
        ('Windows', 'windows/test-results/windows_e2e_results.json'),
    ]:
        if platform == 'Windows' and windows_state == 'missing':
            continue
        result = {'caseId': case['id'], 'status': '合格', 'fingerprint': hashes[0],
                  'executedAt': '2026-01-01', 'video': platform + '.mp4', 'notes': platform}
        if platform == 'Windows':
            if windows_state == 'stale':
                result['fingerprint'] = 'different'
            elif windows_state == 'legacy':
                del result['fingerprint']
            elif windows_state == 'failed':
                result.update(status='不合格', video='')
        target = tmp_path / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps({'results': [result]}), encoding='utf-8')
    rows = generate()
    assert rows[0][7:17] == ('実施日', '環境', '合否', '動画ファイル名', '備考',
                              'Web 実施日', 'Web 環境', 'Web 合否', 'Web 動画ファイル名', 'Web 備考')
    assert rows[0][17:] == ('Windows 実施日', 'Windows 環境', 'Windows 合否',
                             'Windows 動画ファイル名', 'Windows 備考')
    assert rows[1][7:17] == ('2026-01-01', 'API (Spring Boot/H2)', '合格', None, 'API',
                              '2026-01-01', 'Web (React/Chromium)', '合格', 'Web.mp4', 'Web')
    windows = rows[1][17:]
    if windows_state in ('current', 'failed'):
        assert windows[:2] == ('2026-01-01', 'Windows (.NET 8 WPF)')
        assert windows[2:] == (('合格', 'Windows.mp4', 'Windows') if windows_state == 'current'
                               else ('不合格', None, 'Windows'))
    else:
        assert windows[0] is None and windows[3] is None
        assert windows[2] == ('未実施' if windows_state == 'missing' else '未実施/要再実行')
    after = json.loads((tmp_path / 'tests/cases.json').read_text(encoding='utf-8'))
    assert hashes == [fingerprint(c, after['baseline']) for c in after['cases']]
