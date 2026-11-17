"""共通ケースをExcelとAPI向けJSONへ生成（テスト実行はしない）。"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'spec/tools'))
from gen_spec import write_book

baseline = {
    '説明': '各ケース前に独立リセット。特記なき利用者はMM2024。展示・コメント・ページは検証専用の仮定でありSAVF実測ではない。管理者パスワードはテスト時に環境変数 VCF_SEED_ADMPSWRD で注入。APIのURL/HTTPステータスは実装時に決定。',
    'settings': {'ALWVOTE': 'Y', 'ADMPSWRD': '${VCF_SEED_ADMPSWRD}'},
    'exhibits': [
        {'EXHBDBID': 1, 'EXHUSRPRF': 'EXH001', 'EXHBITOR': '展示者一', 'EXHBCITY': 'Atlanta', 'EXHBSTATE': 'GA', 'EXHBTITLE': '展示一', 'EXHBDESC': '展示説明一', 'ELIGIBLE': 1, 'ENLRN400': 1},
        {'EXHBDBID': 2, 'EXHUSRPRF': 'EXH002', 'EXHBITOR': '展示者二', 'EXHBCITY': 'Atlanta', 'EXHBSTATE': 'GA', 'EXHBTITLE': '展示二', 'EXHBDESC': '展示説明二', 'ELIGIBLE': 0, 'ENLRN400': 0},
    ],
    'awards': [{'AWARDID': 1, 'AWARDTITLE': 'Best in Show'}, {'AWARDID': 2, 'AWARDTITLE': 'Ed Fair'}],
    'votes': [{'BADGENBR': 1001, 'AWARDNBR': 1, 'EXHBNBR': 'EXH001'}],
    'comments': [
        {'CMTID': 1, 'VISIBLE': 'Y', 'EXHBID': 'EXH001', 'GUESTNAME': 'Alice', 'GUESTCMT': 'Great exhibit'},
        {'CMTID': 3, 'VISIBLE': 'N', 'EXHBID': 'EXH001', 'GUESTNAME': 'Bob', 'GUESTCMT': '非公開原文'},
        {'CMTID': 5, 'VISIBLE': 'Y', 'EXHBID': 'EXH002', 'GUESTNAME': 'Carol', 'GUESTCMT': 'Last comment'},
    ],
    'pages': [
        {'PAGENBR': 1, 'CONTENT': '開始', 'EXTRA': ''},
        {'PAGENBR': 2, 'CONTENT': '通常ページ', 'EXTRA': ''},
        {'PAGENBR': 3, 'CONTENT': 'JUMP', 'EXTRA': '0005'},
        {'PAGENBR': 4, 'CONTENT': '読み飛ばすページ', 'EXTRA': ''},
        {'PAGENBR': 5, 'CONTENT': '移動先', 'EXTRA': ''},
        {'PAGENBR': 6, 'CONTENT': 'CALL', 'EXTRA': 'CREDITS'},
        {'PAGENBR': 7, 'CONTENT': '呼出し復帰先', 'EXTRA': ''},
        {'PAGENBR': 8, 'CONTENT': '終了ページ', 'EXTRA': 'END'},
    ],
    'pageCollections': {'global': 'pages', 'EXH001': 'pages'},
}
cases = []


def add(function, name, inputs, expected, refs, screen, steps=None, setup=None):
    identifier = f'E2E-{len(cases) + 1:03}'
    cases.append({'id': identifier, 'function': function, 'name': name,
                  'preconditions': 'baselineを毎回リセット。' + (json.dumps(setup, ensure_ascii=False) if setup else '追加条件なし。'),
                  'setup': setup or {}, 'inputs': inputs, 'expected': expected,
                  'steps': steps or ['対象機能を開く', 'inputsの順序で入力・操作する', 'expectedと保存データを照合する'],
                  'screen': screen, 'specIds': refs.split(), 'execution': {'status': '未実施'}})


def vote(name, badge=1002, exhibit='EXH001', award=1, expected=None, refs='B-01 B-07', setup=None):
    add('投票', name, {'badge': badge, 'exhibit': exhibit, 'award': award}, expected or {'saved': True, 'screen': 'VOTEEND'}, refs, 'VOTE1 → VOTEEND', setup=setup)


vote('正常投票・Best in Show')
vote('正常投票・Ed Fair', award=2)
vote('重複バッジ・別賞でも拒否', badge=1001, award=2, expected={'saved': False, 'error': 'You have already voted.'}, refs='B-03 B-07')
vote('不適格展示', exhibit='EXH002', expected={'saved': False, 'error': 'Exhibit ineligible for award'}, refs='B-04 B-07')
vote('存在しない展示', exhibit='EXH999', expected={'saved': False, 'error': 'Exhibit does not exist'}, refs='B-05 B-07')
vote('存在しない賞', award=999, expected={'saved': False, 'error': 'Award does not exist.'}, refs='B-06 B-07')
vote('全チェック失敗・最後のエラー優先', badge=1001, exhibit='EXH999', award=999, expected={'saved': False, 'error': 'Award does not exist.'}, refs='B-03 B-04 B-05 B-06 B-07')
vote('重複と不適格・適格性エラー優先', badge=1001, exhibit='EXH002', expected={'saved': False, 'error': 'Exhibit ineligible for award'}, refs='B-03 B-04 B-07')
add('投票', '投票期間終了・画面スキップ', {'selectMenuOption': 11},
    {'saved': False, 'screen': 'ENDOFCON', 'voteFormVisible': False}, 'B-08', 'ENDOFCON',
    steps=['VCFMAINで11（またはEXHBMENUで1）を選択する',
           'バッジ・展示・アワードの入力画面が表示されないことを確認する',
           'ENDOFCONの The voting period has ended and you can no longer vote. を確認する'],
    setup={'settings': {'ALWVOTE': 'N'}})
for name, kwargs, error in [('バッジ未入力', {'badge': 0}, 'Must enter badge number'), ('展示未入力', {'exhibit': ''}, 'Must enter Exhibit ID'), ('賞未入力', {'award': 0}, 'Must enter Award ID'), ('全項目未入力', {'badge': 0, 'exhibit': '', 'award': 0}, 'Must enter Exhibit ID')]:
    vote(name, **kwargs, expected={'saved': False, 'error': error}, refs='B-02')
add('投票', 'キオスク展示固定', {'profile': 'EXH001', 'open': 'vote'}, {'exhibit': 'EXH001', 'exhibitEditable': False}, 'B-09', 'VOTE1')
add('投票', 'F12取消・書込みなし', {'action': 'F12'}, {'saved': False, 'returned': True}, 'B-01', 'VOTE1')
add('投票', 'DAVE隠し画面', {'badge': 1002, 'exhibit': 'DAVE', 'award': 1}, {'screen': 'DAVE', 'saved': False}, 'B-26', 'DAVE')
add('ゲストブック投稿', '正常登録・max+1', {'name': 'Visitor', 'exhibit': 'EXH001', 'comment': 'Hello'}, {'saved': True, 'CMTID': 6, 'VISIBLE': 'Y', 'screen': 'ENDCMT'}, 'B-10 B-11', 'ADDCMT → ENDCMT')
add('ゲストブック投稿', '空DBへの初回登録', {'name': 'Visitor', 'exhibit': 'EXH001', 'comment': 'First'}, {'saved': True, 'CMTID': 1, 'VISIBLE': 'Y'}, 'B-11', 'ADDCMT', setup={'comments': []})
for field, error in [('name', 'Must enter your name'), ('exhibit', 'Must enter Exhibit ID'), ('comment', 'Must enter a comment')]:
    inputs = {'name': 'Visitor', 'exhibit': 'EXH001', 'comment': 'Hello'}
    inputs[field] = ''
    add('ゲストブック投稿', f'{field}未入力', inputs, {'saved': False, 'errorDisplay': error}, 'B-10', 'ADDCMT')
add('ゲストブック投稿', '全項目未入力・最終エラー', {'name': '', 'exhibit': '', 'comment': ''}, {'saved': False, 'errorDisplay': 'Must enter a comment'}, 'B-10', 'ADDCMT')
add('ゲストブック投稿', '存在しない展示も原典は登録', {'name': 'Visitor', 'exhibit': 'EXH999', 'comment': 'Hello'}, {'saved': True, 'CMTID': 6}, 'B-11', 'ADDCMT')
add('ゲストブック投稿', 'F12取消', {'action': 'F12'}, {'saved': False, 'returned': True}, 'B-10', 'ADDCMT')
add('ゲストブック投稿', 'キオスク展示固定', {'profile': 'EXH001', 'open': 'comment'}, {'exhibit': 'EXH001', 'exhibitEditable': False}, 'B-09', 'ADDCMT')
for name, inputs, expected, ref in [
    ('通常閲覧', {'id': 1}, {'name': 'Alice', 'comment': 'Great exhibit', 'title': '展示一'}, 'B-16'),
    ('非表示は名前・本文を置換', {'id': 3}, {'name': 'Name Hidden', 'comment': 'This comment hidden by an admin - offensive content.'}, 'B-14'),
    ('キオスクから別展示は非公開', {'id': 5, 'profile': 'EXH001'}, {'name': '', 'title': '', 'comment': 'This comment is not part of this guestbook.'}, 'B-15'),
    ('同展示は公開', {'id': 1, 'profile': 'EXH001'}, {'name': 'Alice', 'comment': 'Great exhibit'}, 'B-16'),
    ('末尾超過は最終レコード', {'id': 9999}, {'CMTID': 5, 'name': 'Carol', 'comment': 'Last comment'}, 'B-13'),
    ('欠番は次のID', {'id': 4}, {'CMTID': 5, 'name': 'Carol'}, 'B-13'),
    ('ID未入力', {'id': 0}, {'error': 'Must enter CommentID'}, 'B-12'),
    ('表示件数は最終ID', {'open': 'read'}, {'displayedTotal': 5}, 'B-13'),
    ('空DB閲覧', {'id': 1}, {'empty': True, 'note': '移行API方針。原典未測定、例外や他レコード漏洩なし'}, 'O-04'),
]:
    add('ゲストブック閲覧', name, inputs, expected, ref, 'READCMT', setup={'comments': []} if name == '空DB閲覧' else None)
for name, inputs, expected, ref in [
    ('最初のページ', {'open': 'global'}, {'page': 1, 'content': '開始'}, 'B-17'),
    ('通常前進', {'from': 1, 'action': 'F5'}, {'page': 2, 'content': '通常ページ'}, 'B-17'),
    ('JUMP分岐', {'from': 2, 'action': 'F5'}, {'action': 'JUMP', 'page': 5, 'content': '移動先', 'backPage': 2}, 'B-20'),
    ('CALL分岐と復帰', {'from': 5, 'action': 'F5'}, {'action': 'CALL', 'program': 'CREDITS', 'pageAfterReturn': 7, 'backPage': 5}, 'B-19'),
    ('ENDで終了', {'from': 7, 'action': 'F5'}, {'ended': True}, 'B-18'),
    ('通常戻る', {'from': 2, 'action': 'F8'}, {'page': 1}, 'B-21'),
    ('JUMP後戻る', {'from': 5, 'backPage': 2, 'action': 'F8'}, {'page': 2}, 'B-20 B-21'),
    ('CALL後戻る', {'from': 7, 'backPage': 5, 'action': 'F8'}, {'page': 5}, 'B-19 B-21'),
    ('末尾前進', {'from': 8, 'action': 'F5'}, {'content': '', 'allowForward': False}, 'B-21'),
    ('F3終了', {'action': 'F3'}, {'ended': True}, 'B-17'),
    ('展示別ページ', {'open': 'EXH001'}, {'page': 1, 'content': '開始', 'collection': 'EXH001'}, 'B-22'),
    ('ページなし', {'open': 'global'}, {'empty': True, 'note': '移行API方針。原典未測定'}, 'O-04'),
    ('不正JUMP引数', {'from': 2, 'action': 'F5'}, {'validationError': True, 'note': '移行API方針。原典未測定'}, 'O-04'),
]:
    setup = {'pages': []} if name == 'ページなし' else ({'replacePage': {'PAGENBR': 3, 'CONTENT': 'JUMP', 'EXTRA': 'INVALID'}} if name == '不正JUMP引数' else None)
    add('LEARN/400', name, inputs, expected, ref, 'LRN400 MAIN', setup=setup)
add('キオスク', '正しいパスワードで終了', {'profile': 'EXH001', 'option': 7, 'password': '${VCF_SEED_ADMPSWRD}'}, {'exited': True}, 'B-24', 'EXHBMENU → ADMPSWRD')
add('キオスク', '誤ったパスワード', {'profile': 'EXH001', 'option': 7, 'password': 'wrong'}, {'exited': False, 'customError': None}, 'B-24', 'EXHBMENU → ADMPSWRD')
add('キオスク', '空パスワード', {'profile': 'EXH001', 'option': 7, 'password': ''}, {'exited': False}, 'B-24', 'ADMPSWRD')
add('キオスク', '全項目表示・隠し7は非表示', {'profile': 'EXH001'}, {'visibleOptions': [1, 2, 3, 4], 'hiddenOptions': [7], 'title': 'WELCOME TO...'}, 'B-23', 'EXHBMENU')
add('キオスク', '不適格・学習無効', {'profile': 'EXH002'}, {'visibleOptions': [3, 4], 'blockedOptions': [1, 2]}, 'B-23', 'EXHBMENU')
add('展示一覧', '展示データ一覧', {'operation': 'listExhibits'}, {'ids': ['EXH001', 'EXH002'], 'count': 2}, 'D-02', 'API（旧画面に一覧なし）')
add('展示一覧', '空一覧', {'operation': 'listExhibits'}, {'ids': [], 'count': 0}, 'D-02', 'API（旧画面に一覧なし）', setup={'exhibits': []})
add('設定', '投票設定参照', {'operation': 'readSettings'}, {'ALWVOTE': 'Y', 'passwordNotExposed': True}, 'B-08 O-08', 'API')
add('管理者認証', '認証成功', {'password': '${VCF_SEED_ADMPSWRD}'}, {'authenticated': True}, 'B-24 O-08', 'API')
add('管理者認証', '認証失敗', {'password': 'wrong'}, {'authenticated': False, 'passwordNotExposed': True}, 'B-24 O-08', 'API')
add('画面共通', 'メニュー番号・原文タイトル・Fキー・エラー位置照合', {'operation': 'compareLedger'}, {'matchesAllLedgerElements': True}, 'B-27 B-01 B-10 B-12 B-17 B-25', 'VCFMAIN・VOTE1・ADDCMT・READCMT・LRN400・INTERTEST', steps=['仕様書画面台帳の各L-IDを全件照合する', 'VCFMAIN 1/11/12/13/80の経路と表示番号を確認する', '投票はバッジ→展示→賞、投稿は名前→展示→本文、Fキー・エラー位置を照合する', '未定義2～10・90の機能を推測で追加していないことを確認する'])

spec = json.loads((ROOT / 'spec/tools/spec_data.json').read_text())
def ledger_screen(row):
    if row[1] == 'EXHBMENU/7（非表示）':
        return 'ADMPSWRD'
    return row[1].split(' → ')[-1]


def case_screens(case):
    aliases = {'LRN400 MAIN': 'LEARN/400', 'LRN400': 'LEARN/400', 'INTERTEST': 'NTRSTIT'}
    return {aliases.get(part, part) for part in re.split(r' → |・', case['screen'])}


for case in cases:
    for row in spec['画面台帳'][1:]:
        if ledger_screen(row) in case_screens(case):
            case['specIds'].append(row[0])
    case['specIds'] = list(dict.fromkeys(case['specIds']))

ledger = {row[0]: row for row in spec['画面台帳'][1:]}
for case in cases:
    linked = [ledger[item] for item in case['specIds'] if item.startswith('L-')]
    assert all(ledger_screen(row) in case_screens(case) for row in linked), case['id']
    if case['function'] == 'LEARN/400':
        assert linked and all(ledger_screen(row) == 'LEARN/400' for row in linked), case['id']
print(f'画面トレーサビリティ: {len(cases)}ケースのL-IDが対象画面と完全一致、LEARN/400へのVCFMAIN混入0件: PASS')

headers = ['ケースID', '機能', '前提条件', '操作手順', '期待結果', '対象画面', 'トレーサビリティ（仕様書項目ID）']
rows = [headers]
for case in cases:
    rows.append([case['id'], case['function'] + '：' + case['name'], case['preconditions'] + '\n基準データはtests/cases.json baseline参照。', '\n'.join(case['steps']) + '\n入力: ' + json.dumps(case['inputs'], ensure_ascii=False), json.dumps(case['expected'], ensure_ascii=False), case['screen'], ', '.join(case['specIds'])])
write_book(ROOT / 'tests/VCF400_e2e_cases.xlsx', {'E2Eテストケース': rows})
(ROOT / 'tests/cases.json').write_text(json.dumps({'schemaVersion': 1, 'baseline': baseline, 'cases': cases}, ensure_ascii=False, indent=2) + '\n')
print(f'ケース生成: tests/VCF400_e2e_cases.xlsx / tests/cases.json ({len(cases)}件、未実施)')
