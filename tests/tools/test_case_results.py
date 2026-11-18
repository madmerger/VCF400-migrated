import copy
import json
from pathlib import Path
import unittest

from case_results import current_result, fingerprint, result_summary


class CaseResultsTest(unittest.TestCase):
    def setUp(self):
        self.case = {'id': 'E2E-001', 'inputs': {'badge': 1, 'exhibit': '展示'}, 'expected': {'saved': True}}
        self.baseline = {}
        self.result = {'caseId': self.case['id'], 'status': '合格', 'executedAt': '2026-01-01', 'fingerprint': fingerprint(self.case, self.baseline)}

    def test_unchanged_case_retains_result(self):
        self.assertEqual(self.result, current_result(self.case, {self.case['id']: self.result}, self.baseline))
        reordered = {'expected': {'saved': True}, 'inputs': {'exhibit': '展示', 'badge': 1}}
        self.assertEqual(fingerprint(self.case, self.baseline), fingerprint(reordered, self.baseline))

    def test_missing_result_is_empty(self):
        self.assertEqual({}, current_result(self.case, {}, self.baseline))

    def test_changed_input_or_expectation_requires_rerun(self):
        for key, value in [('inputs', {'badge': 2}), ('expected', {'saved': False})]:
            changed = copy.deepcopy(self.case)
            changed[key] = value
            result = current_result(changed, {self.case['id']: self.result}, self.baseline)
            self.assertEqual('未実施/要再実行', result['status'])
            self.assertNotIn('executedAt', result)

    def test_legacy_result_without_fingerprint_requires_rerun(self):
        del self.result['fingerprint']
        self.assertEqual('未実施/要再実行', current_result(self.case, {self.case['id']: self.result}, self.baseline)['status'])

    def test_e2e_009_setup_change_requires_rerun(self):
        document = json.loads((Path(__file__).resolve().parents[1] / 'cases.json').read_text())
        case = next(case for case in document['cases'] if case['id'] == 'E2E-009')
        baseline = document['baseline']
        self.assertEqual('N', case['setup']['settings']['ALWVOTE'])
        result = {'status': '合格', 'fingerprint': fingerprint(case, baseline)}
        self.assertEqual(result, current_result(case, {case['id']: result}, baseline))
        changed = copy.deepcopy(case)
        changed['setup']['settings']['ALWVOTE'] = 'Y'
        self.assertEqual('未実施/要再実行', current_result(changed, {case['id']: result}, baseline)['status'])

    def test_baseline_change_requires_rerun(self):
        changed = {'settings': {'ALWVOTE': 'N'}}
        result = current_result(self.case, {self.case['id']: self.result}, changed)
        self.assertEqual('未実施/要再実行', result['status'])
        self.assertNotIn('executedAt', result)

    def test_summary_counts_current_failed_ui_missing_stale_and_legacy_results(self):
        cases = [dict(self.case, id=f'E2E-{i}') for i in range(6)]
        results = {case['id']: dict(self.result, caseId=case['id'], video='current.mp4') for case in cases}
        results[cases[1]['id']].update(status='不合格', video='failed.mp4')
        results[cases[2]['id']]['status'] = 'UI専用・フェーズ3/4'
        results[cases[3]['id']].update(fingerprint='old', video='stale.mp4')
        del results[cases[4]['id']]
        del results[cases[5]['id']]['fingerprint']
        summary = result_summary(cases, results, self.baseline)
        self.assertEqual({'total': 6, 'passed': 1, 'failed': 1, 'uiOnly': 1, 'unexecuted': 3,
                          'stale': 2, 'video': 'current.mp4', 'executedAt': '2026-01-01'}, summary)

    def test_empty_summary_has_no_results_or_video(self):
        summary = result_summary([], {}, {})
        self.assertEqual({'total': 0, 'passed': 0, 'failed': 0, 'uiOnly': 0, 'unexecuted': 0,
                          'stale': 0, 'video': '', 'executedAt': ''}, summary)


if __name__ == '__main__':
    unittest.main()
