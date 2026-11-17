"""API結果と現在のケース定義を照合する。"""
import hashlib
import json


def fingerprint(case, baseline):
    payload = {key: case[key] for key in ('inputs', 'expected')}
    payload.update(setup=case.get('setup', {}), baseline=baseline)
    canonical = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(',', ':'))
    return hashlib.sha256(canonical.encode('utf-8')).hexdigest()


def current_result(case, results, baseline):
    result = results.get(case['id'], {})
    if result and result.get('fingerprint') != fingerprint(case, baseline):
        return {'status': '未実施/要再実行', 'notes': 'ケース入力・期待値・setup・baselineのfingerprintが結果と不一致（または結果に未記録）'}
    return result
