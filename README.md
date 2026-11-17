# VCF400 移行

## フェーズ1成果物

- `spec/VCF400_spec.xlsx`: 概要・画面台帳・業務ルール・データモデル・非対象スコープ。
- `tests/VCF400_e2e_cases.xlsx`: 共通E2Eケース。まだ実行結果ではない。
- `tests/cases.json`: 独立リセットする基準データ、構造化入力・期待値、仕様ID。

再生成（Python 3、openpyxl 3.1.5）:

```sh
python3 -m pip install openpyxl==3.1.5
python3 spec/tools/gen_spec.py
python3 tests/tools/gen_cases.py
```

原典抽出を更新する場合のみ `python3 spec/tools/extract_source.py /path/to/VCF400` を先に実行する。
通常の生成はコミット済み `spec/tools/spec_data.json` を使用するため原典チェックアウト不要。
原典は master `30bfc334b99c75d28091c7815e4617cb456dd7f4`。

本段階はソース読解による仕様化のみ。IBM i 実機・API・Web・WindowsのE2Eは未実施。
バックエンド実装・APIテストは次工程、Web/Windows・動画・最終結果列の集約は別セッション。
空DB、不正ページ引数等の移行API側の期待値は防御的な方針であり原典実測値ではない。
APIのURL・HTTPステータス・認証契約はバックエンド実装工程で定義する。

VCFMAIN 利用者機能を移行する。構成予定: spec/（仕様）、tests/（共通ケース・結果）、backend/（Spring Boot）、frontend/（React）、windows/（.NET）、video/（録画）。
