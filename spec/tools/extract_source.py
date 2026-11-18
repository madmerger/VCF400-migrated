"""原典のDDSを機械抽出する（通常の再生成には不要）。"""
import json
import re
import sys
from pathlib import Path

source = Path(sys.argv[1])
out = Path(__file__).with_name('spec_data.json')
data = {
    '概要': [['項目ID', '項目', '内容', '根拠']],
    '画面台帳': [['項目ID', 'メニュー経路・画面', '要素', '原文・フィールド・役割', '位置・型長・入力順', '原典']],
    '業務ルール': [['項目ID', '規則', '条件', '結果', '原典']],
    'データモデル': [['項目ID', 'ファイル', 'フィールド', '型・桁数', 'キー', '意味・原典']],
    '非対象スコープ': [['項目ID', '対象', '取り扱い', '根拠']],
}
for title, content in [
    ('目的', 'VCFMAIN 利用者機能の移行。Java 17/Spring Boot・JPA・H2/PostgreSQL、Web、Windowsで共通仕様を使用。UIはモダン化しグリーンスクリーン模写はしない。'),
    ('原典', 'madmerger/VCF400 master 30bfc334b99c75d28091c7815e4617cb456dd7f4。原典は読み取り専用。README、Operator’s Guide、Page Programming Guideも参照。'),
    ('保持事項', 'メニュー階層・番号、入力順、Fキーの番号と役割、タイトル原文、エラー位置と文言、業務ルールを保持。台帳の行・列は旧画面上の位置。'),
    ('検証状況', '本書はソース静的解析。実機未実行、Excelケースは未実施。空DB・ページ欠損・型変換エラーの原典挙動は未測定。移行APIでは検証エラーとし、500やデータ破壊を防ぐ方針（要実装時確認）。'),
    ('基準データ', 'tests/cases.json の baseline を使用。展示・コメント・ページはテスト専用の仮定。AWARD 001=Best in Show、002=Ed Fair。管理者パスワードはテスト時に環境変数 VCF_SEED_ADMPSWRD で注入。未設定時は管理者ログイン無効。'),
    ('原典差異', 'VCFMAINの表示1はLearn AS/400 NavigationだがコマンドはLRN400。表示2～10、90にはVCFMAINQQ内の実行定義なし。欠落機能を推測して実装しない。CREDITSは直接呼出しなし、CALLページによる呼出し候補としてのみ収録。'),
    ('分岐表記補正', "ユーザー前提の EXTRA='CALL'/'JUMP' は原典では CONTENT='CALL'/'JUMP'、EXTRAが引数。ENDのみEXTRA。EXHBMENUは原典でSETTINGS先頭レコードを読むが、確定前提に従いADMPSWRDをキー指定する。"),
    ('安全性', 'ADMPSWRDは管理者認証用。移行先APIで平文返却しない。動的CALLは任意のOSコマンド実行にせず許可済み機能を解決する。認証方式・HTTP契約はフェーズ2で確定。'),
]:
    data['概要'].append([f'O-{len(data["概要"]):02}', title, content, '依頼の確定前提・原典読解'])

screens = {
    'QMNUSRC/VCFMAIN.mnudds': {'VCFMAIN': 'VCFMAIN'},
    'QSDASRC/VOTESCR.dspf': {x: 'VCFMAIN/11・EXHBMENU/1 → ' + x for x in ['VOTE1', 'VOTEEND', 'DAVE', 'ENDOFCON']},
    'QSDASRC/GUESTBKSCR.dspf': {'ADDCMT': 'VCFMAIN/12・EXHBMENU/3 → ADDCMT', 'ENDCMT': '投稿完了 → ENDCMT', 'READCMT': 'VCFMAIN/13・EXHBMENU/4 → READCMT'},
    'QSDASRC/LRN400SCR.dspf': {'MAIN': 'VCFMAIN/1・EXHBMENU/2 → LEARN/400'},
    'QSDASRC/EXHBMENUSC.dspf': {'MENU': 'VCFSTUB → EXHBMENU', 'ADMPSWRD': 'EXHBMENU/7（非表示）'},
    'QSDASRC/INTERSCR.dspf': {'INTERTEST': 'VOTESTUB・ADDGBSTUB・READGBSTUB → NTRSTIT'},
    'QSDASRC/CREDITSSCR.dspf': {'SCREEN1': 'LEARN/400 CALL候補（直接のメニュー経路なし）'},
}
for file, formats in screens.items():
    lines = (source / file).read_text().splitlines()
    record = ''
    for i, line in enumerate(lines):
        match = re.search(r' A\s+R ([#\w]+)', line)
        if match:
            record = match[1]
        if record not in formats or 'A*' in line:
            continue
        field = re.search(r' A\s{12}(\w+)\s+(\d+)([ADY ]?)\s*([0-9]?)\s*([BIO])\s+(\d+)\s+(\d+)', line)
        literal = re.search(r"\s(\d+)\s+(\d+)'(.*)", line)
        key = re.search(r'CF(\d+)(?:\(\d+ \'([^\']*)\'\))?', line)
        if literal:
            value = literal[3]
            j = i
            while not value.endswith("'") and j + 1 < len(lines):
                j += 1
                part = lines[j][lines[j].index('A') + 1:].strip()
                value = value[:-1] + part if value.endswith('-') else value + part
            value = value[:-1] if value.endswith("'") else value
            element, position = '固定文言（タイトル・ラベル等）', f'行{literal[1]} 列{literal[2]}'
        elif field:
            value = field[1]
            element = 'エラー行' if value == 'ERRLINE' else ('入力' if field[5] in 'IB' else '出力')
            position = f'行{field[6]} 列{field[7]} / {field[2]}{field[3]} 小数{field[4] or 0} / {field[5]}'
        elif key:
            value, element, position = f'F{int(key[1])} {key[2] or "メニュー標準"}', 'Fキー', 'レコード定義'
        else:
            continue
        data['画面台帳'].append([f'L-{len(data["画面台帳"]):03}', formats[record], element, value, position, f'{file}:{i + 1}'])

rules = [
 ('投票入力順', 'VOTE1', 'バッジ4桁 → 展示ID9文字 → アワードID3桁。F5/Enter送信、F12取消。F5のみ強制ではない。', 'ADDVOTE:40-79; QSDASRC/VOTESCR.dspf:11-22'),
 ('必須入力', 'バッジ0・アワード0・展示空白', '順に検査、最後の失敗で上書き。Must enter badge number / Must enter Award ID / Must enter Exhibit ID', 'ADDVOTE:42-61'),
 ('バッジ重複チェック1', 'BADGENBRが既存', 'You have already voted. / バッジ単位で1票（アワード別ではない）', 'ADDVOTE:84-89'),
 ('適格性チェック2', 'ELIGIBLE=0', 'Exhibit ineligible for award / それ以外は加点。原典はSETLL/READ後に検査。', 'ADDVOTE:91-98'),
 ('展示存在チェック3', '展示IDが存在しない', 'Exhibit does not exist', 'ADDVOTE:100-105'),
 ('アワード存在チェック4', 'アワードIDが存在しない', 'Award does not exist.', 'ADDVOTE:107-112'),
 ('4チェック全成功', 'CHECKOK=4', '投票登録、VOTEENDへ。失敗時登録しない。ERRLINEは後の失敗が優先（award > exhibit > eligible > duplicate）、行6列14・45文字。', 'ADDVOTE:81-122'),
 ('投票期間終了', "SETTINGS.ALWVOTE='N'", '投票画面を出さずENDOFCON。The voting period has ended and you can no longer vote.', 'ADDVOTE:137-151'),
 ('展示固定', "LAUNCH <> 'MM2024'", '投票INEXHBと投稿INIDを利用者プロファイルで固定（保護）。MM2024では自由入力。', 'ADDVOTE:124-135; ADDGBCMT:81-89'),
 ('投稿入力・必須', '名前16文字 → 展示ID9文字 → コメント200文字', 'F5/Enter送信、F12取消。展示・名前・コメント順の必須検査。Must enter Exhibit ID / Must enter your name    / Must enter a comment。行10列5・21文字へ代入し長文は切詰め。', 'ADDGBCMT:24-59; QSDASRC/GUESTBKSCR.dspf:4-27'),
 ('コメント登録', '必須入力あり', "CMTID=max+1、VISIBLE='Y'。名前・展示ID・本文保存。ENDCMTの完了文はComment ID Is:とあるがID出力フィールドなし。展示存在検査は原典にはない。", 'ADDGBCMT:61-79'),
 ('閲覧入力', 'INCMTID=0', 'Must enter CommentID / 行4列4・20文字。F5/Enter表示、F12取消。', 'READGBCMT:26-50; QSDASRC/GUESTBKSCR.dspf:78-108'),
 ('閲覧検索・末尾', '指定ID以上をSETLL/READ。末尾超過', '次のレコード。末尾超過は最後のレコードへフォールバック（確定前提）。表示件数は件数COUNTではなく最終CMTID。', 'READGBCMT:53-86'),
 ('非表示コメント', "VISIBLE='N'", 'Name Hidden / This comment hidden by an admin - offensive content.（本文も置換）。利用者・管理者とも優先適用。', 'READGBCMT:57-60'),
 ('別展示コメント', "VISIBLE<>'N' かつ EXHBID<>USERPRF かつ USERPRF<>'MM2024'", 'This comment is not part of this guestbook. / 名前とタイトルを空白化。', 'READGBCMT:61-73'),
 ('通常閲覧', '同展示またはMM2024', '名前（表示11文字）・本文・展示タイトル（50文字）を表示。', 'READGBCMT:61-67'),
 ('LEARN初期・操作', '先頭レコード表示', 'F3終了、F5前進、F8後退。通常ページは1500文字。初期表示時はPARSESCRIPTを呼ばず前進時に解決。', 'LRN400:11-35;93-130'),
 ('LEARN END', "EXTRA='END'", '前進時に終了。CONTENTがCALL/JUMPなら後段判定に上書きされる。', 'LRN400:37-59'),
 ('LEARN CALL', "CONTENT='CALL'", 'EXTRAのプログラムを呼び出す。復帰後CURPAGENBR+1へ。戻り先は分岐ページ-1。', 'LRN400:42-65;73-91'),
 ('LEARN JUMP', "CONTENT='JUMP'", 'EXTRAのページ番号へ（先頭ゼロ付き）。戻り先は分岐ページ-1。', 'LRN400:46-48;67-91'),
 ('LEARN末尾・戻る', '前進対象なし / 分岐後F8', '対象なしでは内容空白、ALWFWD=0。F8はFRMPAGENBRがあれば分岐前ページへ、なければ1減算（0より下げない）。JUMPTO先で再帰解決はしない。', 'LRN400:93-130'),
 ('展示専用データ', 'LRN400STUB', '現在のプロファイル名のファイルにLRN400STRをオーバーライドし、終了時解除。移行では展示単位のページ集合として扱う。', 'QCLSRC/LRN400STUB.clp:2-8'),
 ('キオスク項目', 'EXHBMENU', '1=投票（ELIGIBLE=1のみ表示・起動）、2=LEARN（ENLRN400=1のみ）、3=投稿、4=閲覧。非表示7=管理者パスワード。', 'EXHBMENU:16-46;62-86'),
 ('管理者終了', 'EXHBMENU/7', 'ADMPSWRDに一致すればキオスク終了。不一致は終了しない。専用エラーテキスト・Fキー定義なし。原典はSETTINGS先頭を読むが確定前提でADMPSWRDを参照。', 'EXHBMENU:48-60'),
 ('導入案内', 'VCFMAIN/11・12・13の各stub', '処理前にNTRSTIT。VCFSTUB経由も同じstubを使用。ARROW/TAB移動、F5/ENTER操作、F12終了の案内。', 'QCLSRC/VOTESTUB.clp:5-6; QCLSRC/ADDGBSTUB.clp:5-6; QCLSRC/READGBSTUB.clp:5-6'),
 ('DAVE隠し分岐', "INEXHB='DAVE'", 'DAVE表示後に終了。通常投票は保存しない。', 'ADDVOTE:63-65;153-158'),
 ('メニューコマンド', 'VCFMAIN', '1=LRN400、11=VOTESTUB、12=ADDGBSTUB、13=READGBSTUB、80=signoff。その他の表示項目は実行定義欠落。', 'QMNUSRC/VCFMAINQQ.mnucmd:2-6'),
]
for screen, text, position, ref in [
    ('VOTE1', 'バッジ → 展示ID → アワードID', '入力順1→2→3', 'QSDASRC/VOTESCR.dspf:11-22'),
    ('ADDCMT', '名前 → 展示ID → コメント', '入力順1→2→3', 'QSDASRC/GUESTBKSCR.dspf:10-25'),
    ('VOTE1', 'Must enter badge number | Must enter Award ID | Must enter Exhibit ID | You have already voted. | Exhibit ineligible for award | Exhibit does not exist | Award does not exist.', '行6列14・45文字。個別エラー、最後の失敗が表示', 'QRPGLESRC/ADDVOTE.rpgle:16-23;42-61;84-112'),
    ('ADDCMT', 'Must enter Exhibit ID | Must enter your name    | Must enter a comment', '行10列5・21文字。原文を21文字に切詰め', 'QRPGLESRC/ADDGBCMT.rpgle:10-13;26-44'),
    ('READCMT', 'Must enter CommentID', '行4列4・20文字', 'QRPGLESRC/READGBCMT.rpgle:8;32-34'),
]:
    for element in text.split(' | '):
        data['画面台帳'].append([f'L-{len(data["画面台帳"]):03}', screen, '入力順' if '入力順' in position else 'エラー文言', element, position, ref])

for title, condition, result, ref in rules:
    data['業務ルール'].append([f'B-{len(data["業務ルール"]):02}', title, condition, result, ref.replace('ADDVOTE:', 'QRPGLESRC/ADDVOTE.rpgle:').replace('ADDGBCMT:', 'QRPGLESRC/ADDGBCMT.rpgle:').replace('READGBCMT:', 'QRPGLESRC/READGBCMT.rpgle:').replace('LRN400:', 'QRPGLESRC/LRN400.rpgle:').replace('EXHBMENU:', 'QRPGLESRC/EXHBMENU.rpgle:')])

meanings = dict(EXHBDBID='展示管理番号', EXHUSRPRF='展示ID・関連利用者', EXHBITOR='展示者', EXHBCITY='市', EXHBSTATE='州', EXHBTITLE='展示タイトル', EXHBDESC='展示説明', ELIGIBLE='投票可否0/1', ENLRN400='学習可否0/1', BADGENBR='バッジ番号', AWARDNBR='投票先賞', EXHBNBR='投票先展示', CMTID='コメント番号', VISIBLE='公開Y・非表示N', EXHBID='コメント先展示', GUESTNAME='投稿者名', GUESTCMT='本文', AWARDID='賞番号', AWARDTITLE='賞名', AWARDDESC='賞説明', SETTING='設定名 ALWVOTE/ADMPSWRD', VALUE='設定値', USERPROF='管理者プロファイル（利用者機能では未参照）', PAGENBR='ページ番号', CONTENT='本文・CALL/JUMP命令', EXTRA='END・呼出し先・移動先')
for name in ['EXHBDB', 'VOTINGDB', 'GUESTBKDB', 'AWARDDB', 'SETTINGS', 'SECOFRS', 'LRN400STR']:
    file = f'QDDSSRC/{name}.pf'
    lines = (source / file).read_text().splitlines()
    keys = re.findall(r' A\s+K (\w+)', '\n'.join(lines))
    for i, line in enumerate(lines, 1):
        match = re.search(r' A\s{12}(\w+)\s+(\d+)([AB])(?:\s+(\d+))?', line)
        if match:
            field, length, kind, decimals = match.groups()
            data['データモデル'].append([f'D-{len(data["データモデル"]):02}', name, field, f'{kind} {length}桁' + (f' 小数{decimals or 0}（DDS数値桁、バイト数ではない）' if kind == 'B' else '文字'), '一意キー' if field in keys else '非キー（外部キー宣言なし）', meanings[field] + f' / {file}:{i}'])
for name, why in [('ADMMAIN系', '管理機能一式は非対象。SECOFRSはモデルのみ記載。'), ('VCFMAIN 90', 'Start Admin Menuは非対象。'), ('PRTLSTVOTE/PRTLSTCMT', '投票・投稿後の印刷呼出しは言及のみ。スプールは移植しない。'), ('BMOVDB', '非対象DB。'), ('OLDVOTE/OLDADDVOTE/PRINTER/BEEMOVIE', '旧版・印刷・余興プログラムは言及のみ。'), ('PASSSCR/管理DDS形式', 'PASSSCRは対象プログラムからの参照なし。ADMCREATE/ADMDELETE/HIDECMT/MAINADMINは管理用。利用者画面として実装しない。'), ('VCFMAIN 2～10', '番号・表示文言は保持するが外部オフィス・ゲームの実装は対象利用者機能に含めない。')]:
    data['非対象スコープ'].append([f'X-{len(data["非対象スコープ"]):02}', name, why, '依頼スコープ・原典呼出し定義'])
out.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')
print('原典抽出: spec/tools/spec_data.json')
