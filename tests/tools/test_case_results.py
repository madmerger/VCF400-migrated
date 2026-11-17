import copy
import unittest

from case_results import current_result, fingerprint


class CaseResultsTest(unittest.TestCase):
    def setUp(self):
        self.case = {'id': 'E2E-001', 'inputs': {'badge': 1, 'exhibit': '展示'}, 'expected': {'saved': True}}
        self.result = {'caseId': self.case['id'], 'status': '合格', 'executedAt': '2026-01-01', 'fingerprint': fingerprint(self.case)}

    def test_unchanged_case_retains_result(self):
        self.assertEqual(self.result, current_result(self.case, {self.case['id']: self.result}))
        reordered = {'expected': {'saved': True}, 'inputs': {'exhibit': '展示', 'badge': 1}}
        self.assertEqual(fingerprint(self.case), fingerprint(reordered))

    def test_missing_result_is_empty(self):
        self.assertEqual({}, current_result(self.case, {}))

    def test_changed_input_or_expectation_requires_rerun(self):
        for key, value in [('inputs', {'badge': 2}), ('expected', {'saved': False})]:
            changed = copy.deepcopy(self.case)
            changed[key] = value
            result = current_result(changed, {self.case['id']: self.result})
            self.assertEqual('未実施/要再実行', result['status'])
            self.assertNotIn('executedAt', result)

    def test_legacy_result_without_fingerprint_requires_rerun(self):
        del self.result['fingerprint']
        self.assertEqual('未実施/要再実行', current_result(self.case, {self.case['id']: self.result})['status'])


if __name__ == '__main__':
    unittest.main()
