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


def result_summary(cases, results, baseline):
    current = [current_result(case, results, baseline) for case in cases]
    statuses = [result.get('status', '未実施') for result in current]
    return {
        'total': len(cases),
        'passed': statuses.count('合格'),
        'failed': statuses.count('不合格'),
        'uiOnly': sum(status.startswith('UI専用') for status in statuses),
        'unexecuted': sum(status not in ('合格', '不合格') and not status.startswith('UI専用') for status in statuses),
        'stale': statuses.count('未実施/要再実行'),
        'video': ', '.join(sorted({result['video'] for result in current if result.get('status') == '合格' and result.get('video')})),
        'executedAt': max((result['executedAt'] for result in current if result.get('executedAt')), default=''),
    }
