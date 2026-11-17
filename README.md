# VCF400 移行 — Phase 5 統合

## フェーズ1成果物

- `spec/VCF400_spec.xlsx`: 概要・画面台帳・業務ルール・データモデル・非対象スコープ。
- `tests/VCF400_e2e_cases.xlsx`: 共通E2Eケースと環境別の実行結果。
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

仕様書はソース読解に基づく。IBM i実機は未検証。WindowsはPR #4の実施結果を継承する。
バックエンド実装・APIテストは `backend/`、Web UI・ブラウザーテストは `frontend/` を参照。
空DB、不正ページ引数等の移行API側の期待値は防御的な方針であり原典実測値ではない。
APIのURL・HTTPステータス・認証契約は `backend/README.md` に記載。

VCFMAIN 利用者機能を移行する。構成: spec/（仕様）、tests/（共通ケース・結果）、backend/（Spring Boot）、frontend/（React）、windows/（.NET WPF）、video/（録画）。UIはモダンなカード形式で、グリーンスクリーン模写ではない。

## フェーズ2 バックエンド

Java 17とMavenを用意し、`cd backend && mvn -s maven-settings-gcs.xml spring-boot:run` で起動する。
既定URLは `http://127.0.0.1:8080/api/menu`。既定H2はメモリーDB。
テストは `cd backend && mvn -s maven-settings-gcs.xml -q test`。
管理者ログインを使う場合のみ環境変数 `VCF_SEED_ADMPSWRD`（9文字以内）を注入する。
未設定なら管理者ログインは無効。認証情報の既定値はない。
API一覧、PostgreSQLプロファイル、保存方式、安全性の制約は [backend/README.md](backend/README.md) を参照。
共通ケース結果は `backend/test-results/` に出力し、`python3 tests/tools/gen_cases.py` でExcel結果列に反映する。

## フェーズ3 Web

React + TypeScript + Vite。`cd frontend && npm ci && npm run dev -- --port 5173`。
バックエンドを8080で起動し、`http://127.0.0.1:5173`を開く。
`npm run build`、`npm run test:e2e`、`npm run video`でビルド・共通58ケース・H.264結合録画を生成する。
E2E実行時はサーバーを自動起動するため手動サーバーは停止しておく。
詳細、テスト専用リセットの安全境界、動画・Excelの環境別列構成は[frontend/README.md](frontend/README.md)を参照。

## フェーズ4 Windows

Windowsの対話デスクトップと.NET 8 SDKを使用し、backendは別途起動する。
リポジトリルートからPowerShellで:

```powershell
dotnet build windows/VCF400.Windows.sln -c Release
dotnet run --project windows/src/VCF400.Windows -c Release --no-build
# E2E（専用backendを自動起動。Java 17 / Maven / Python / ffmpeg等も必要）
dotnet run --project windows/tests/VCF400.Windows.E2E -c Release --no-build
```

管理者入口は展示キオスクのSelectionへ隠し番号`7`を入力する。
API接続先・HTTPS制限・実行前提は [windows/README.md](windows/README.md) を参照。
LinuxではWindows E2Eを実行できない。

## フェーズ5 統合結果

| 環境 | 共通ケース | 合格 | 不合格 | UI専用 | 未実施 | 動画 | 実施範囲 |
|---|---:|---:|---:|---:|---:|---|---|
| API (Spring Boot/H2) | 58 | 55 | 0 | 3 | 0 | なし | 統合backendで再実行 |
| Web (React/Chromium) | 58 | 58 | 0 | 0 | 0 | [2_Web_React.mp4](video/2_Web_React.mp4) | 統合backendで再実行 |
| Windows (.NET 8 WPF) | 58 | 58 | 0 | 0 | 0 | [3_Windows.mp4](video/3_Windows.mp4) | **PR #4の既存結果を維持。統合backendでは未再実行** |

APIは全JUnit **91件**、Webは共通58件＋回帰5件の **63件** が合格。
APIのUI専用3件はE2E-015・024・058で、API合格数に算入しない。
Windowsの実行結果JSON・Markdown・動画はPR #4から変更していない。
3環境とも58/58件で現在のケースfingerprintが一致する。
fingerprintは`inputs`・`expected`・`setup`・`baseline`のSHA-256で、**backendコード自体は含まない**。
従ってWindowsの一致はケース定義の整合性確認であり、統合backendでの動作保証ではない。

Excelの詳細シートは各環境に実施日・環境・合否・動画ファイル名・備考の5列を持つ。
`結果サマリ`はケース別のfingerprint照合後に集計し、不一致・欠落は未実施、うち要再実行を別計上する。
日時・環境別の実際値／期待値は各`test-results/`のJSON・Markdownを参照。
動画は同一runIdの全共通ケース成功後に結合したものだけ結果へ紐付ける。
Web動画は4,266,081 bytes、Windows動画は2,630,846 bytes。いずれも100MB未満のためGit LFSは不要。
APIの録画はなく、`1_`動画は作成していない。

### 再実行・集約コマンド

```sh
# backend/
mvn -s maven-settings-gcs.xml -q test
# frontend/（初回はnpm ci。Chromium未導入ならnpx playwright install chromium）
npm run build
VCF_BACKEND_PORT=18080 VCF_WEB_PORT=15173 npm run test:e2e
npm run video
# リポジトリルート（Python 3 / openpyxl / pytest）
python3 tests/tools/gen_cases.py
python3 -m unittest discover -s tests/tools -p 'test_*.py' -v
python3 -m pytest tests/tools -q
```

Web E2Eは指定ポートでbackendとViteを自動起動するため、使用ポートを空ける。
管理者パスワード・リセットキーは実行時にランダム注入し、各ケース前に実H2をリセットする。
独立lintスクリプトはなく、buildがTypeScript検査を含む。

## 既知の制限

- PUB400 / IBM i実機・SAVFの実データでは未検証。baselineには検証専用の仮定がある。
- PostgreSQL実サーバーでは未検証。API/Web E2Eは実H2を使用する。
- Windowsの高DPI・実物キーボード操作は未検証。Phase 5ではLinuxからWindows E2Eを再実行していない。
- パスワード変更と認証、投票と受付締切の排他は**単一アプリインスタンス内**。
  複数インスタンス間の締切排他、token共有、本番認証のレート制限は未実装。
- Webの対象はChromium。DAVE本文・CALL先の詳細本文は既存APIの対象外で、遷移・タイトルまでを扱う。
- `npm ci` / `npm audit`は既存依存にhigh 4件（vite / tinyglobby / fdir / picomatch）を報告する。
  Phase 5では依存更新を行っていない。
