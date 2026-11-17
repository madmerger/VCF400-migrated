import copy
import json
from pathlib import Path
import unittest

from case_results import current_result, fingerprint


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


if __name__ == '__main__':
    unittest.main()
