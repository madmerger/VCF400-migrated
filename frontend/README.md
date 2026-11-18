# VCF/400 Web — React / TypeScript / Vite

既存REST APIを同一オリジンの`/api`経由で使用。Viteは`127.0.0.1:8080`へプロキシする。
メニュー番号、APIの画面タイトル・Fキー・業務エラー文言を保持し、カード形式のモダンなUIにした。
F3/F5/F8/F12はボタンとキーボードの両方に対応する。エラー欄は常設し、旧行・列・長さをdata属性で保持する（5250座標配置の模写はしない）。

## 手動起動

Node.js 22.12以上、Java 17、Mavenを使用する。

```sh
# 初回のみ、frontend/で:
npm ci
# ターミナル1、backend/で:
# 管理者認証が必要ならVCF_SEED_ADMPSWRDを任意の9文字以内の値に設定する。
mvn -s maven-settings-gcs.xml spring-boot:run
# ターミナル2、frontend/で:
npm run dev -- --port 5173
```

`http://127.0.0.1:5173`を開く。展示を探す→展示を開くで端末コンテキストを切り替える。
キオスクのSelectionへ7を入力すると管理者認証。認証後に投票受付を変更できる。
認証tokenはメモリーにのみ保持し、ページ再読み込みでUIから消去する（サーバー側では10分期限）。パスワードは送信後に入力欄から除去する。
`/?collection=global&page=8`のようなLEARNページへの直接リンクも利用できる。
先頭ページで戻り先のないF8は無効（物理キーも無操作）。原典`LRN400.rpgle:112-129`はカーソルが0なら減算せず、
1なら0へ減算した後CHAIN/READEで次レコードを読み直す。DDSのCF08は無条件定義。
この位置決め処理を移行APIのページ0直接取得とは扱わず、Webでは先頭ページを維持する（IBM i実機での表示結果は未検証）。
JUMP/CALLの明示的なbackPageがある場合はその戻り先を優先する。

## 検証・録画

8080/5173ポートを空けた状態でfrontend/から実行:

```sh
npm run build
# このプロジェクトには独立したlintスクリプトはない。buildにstrict TypeScript検査を含む。
# 利用可能なChromiumがない環境では一度だけ:
npx playwright install chromium
npm run test:e2e
npm run video
# 最後にリポジトリルートで:
python3 tests/tools/gen_cases.py
```

PlaywrightはバックエンドとViteを起動・終了する。既存サーバーは再利用しない。
VCF_SEED_ADMPSWRDとVCF_E2E_KEYを実行時にランダム生成しバックエンド・テストへ渡す。
共通JSONの58ケースを1件ずつUI操作し、ケース間で実H2をリセットする。F12取消2件と台帳照合も含む。
APIだけのassertで合格にはせず、表示内容・入力順・固定展示・分岐画面・エラーをDOMで照合し、保存件数を別途確認する。
認証成功ケースでは設定変更→投票画面スキップも操作する。動画にパスワードを表示せず、tokenを結果へ保存しない。

- ブラウザー: `$PLAYWRIGHT_CHROMIUM_EXECUTABLE`を優先。Linuxの既存キャッシュ`~/.cache/ms-playwright/chromium-1255`があれば使用し、なければPlaywright標準ブラウザー。
- 個別録画: `test-results/artifacts/**/video.webm`（git対象外）。画面内にケースID・名称を表示、1280×720。
- 結果: `test-results/web_e2e_results.json` / `.md`。APIと同じinputs・expected・setup・baselineのcanonical UTF-8 JSON/SHA-256。
- スクリーンショット: `test-results/screenshots/`。
- 結合動画: `../video/2_Web_React.mp4`（H.264 / yuv420p / 1280×720）。`ffmpeg`と`ffprobe`が必要。58件すべて成功した録画がそろった場合のみ生成する。
- 起動ログ: `.e2e/backend.log`（git対象外）。

Excelは既存のAPI結果5列を維持し、その右にWeb結果5列（`Web 実施日`、`Web 環境`、`Web 合否`、`Web 動画ファイル名`、`Web 備考`）を追加する。
Web環境は`Web (React/Chromium)`。Windowsも同様の列グループを追加できる。
各環境のfingerprintを個別に検証するため、一方の再実行が他方の結果を消さない。

## E2E用バックエンド拡張

既存利用者APIの契約は変更していない。Java内部resetだけでは別プロセスからケース条件を投入できないため、
`e2e`プロファイル限定で`POST /api/e2e/reset`と`GET /api/e2e/counts`を追加した。
どちらも非空の環境変数`VCF_E2E_KEY`と同じ`X-E2E-Key`ヘッダーが必要。
本番・共有DBでe2eプロファイルを有効にしないこと。既定の127.0.0.1バインドを維持する。
通常プロファイルではエンドポイント自体が存在しないことをJUnitで確認する。

## 範囲・留意点

非対象メニュー番号も原文のまま表示し、未実装項目を無効にする。DAVE画面はバックエンドにタイトル・本文がないため画面IDのみ表示。
CALLはバックエンドの許可リストで返るタイトルのダイアログを表示し、閉じると復帰ページを表示する。任意プログラムは実行しない。
画面台帳照合は完了画面・DAVE・キオスク・パスワード・CALL先を含む全対象画面と、移行追加の設定・サインオフ画面へ実際に遷移する。
対象画面一覧をcases.jsonのE2E-058 inputs.screensへ記録し、検証済み画面との完全一致を確認する。
結果の動画列はテスト直後には空欄。`npm run video`が同じrunIdの58件合格録画を結合し終えた時のみJSON/Markdownへ動画名を設定する。
失敗・録画欠落・動画未生成の場合は空欄のまま（以前のMP4が残っていても参照しない）。その後Excelを再生成する。
メニュー・タイトル・Fキー・入力順・エラー位置メタデータの検査であり、5250の全255装飾要素をピクセル再現するものではない。
IBM i実機、PostgreSQL実サーバー、他ブラウザー、WindowsネイティブUIはこのWeb検証に含まれない。
