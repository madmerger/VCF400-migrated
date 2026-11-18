# フェーズ3 Web検証

環境: Linux / Node.js 24.22.2 / React 19 / TypeScript 5 / Vite 7.3.8 / Playwright 1.65.1 / Chromium。
バックエンドは別プロセスのSpring Boot 3.4.4 + 実H2。Viteプロキシを通して実HTTP通信した。

## 実行結果

`npm run build`（strict TypeScript検査を含む）:

```text
✓ 29 modules transformed.
✓ built in 752ms
```

独立したlintスクリプトは未設定。

`npm run test:e2e`:

```text
63 passed (1.2m)
```

共通58ケースすべてUIで実行。F12取消2件と画面台帳照合も合格。
追加5件はサインオフ/F3後のtoken失効2件、LEARN EOF後F8、先頭F8、動画結果の成功/空/失敗/欠落・別run検証。
既存checkoutは変更せず別worktree、バックエンド18080/Vite15173で実行した。
入力・画面表示・キー操作に加え、各ケース前後の実DB件数を確認した。
管理者認証成功ケースでは投票受付変更と投票画面スキップまで操作した。
通信失敗時の表示・復帰も画面台帳ケースで確認した。
初回にLEARNの初期データ受信前に物理キーを送って4件失敗したため、UIのbusy解除を待ってから操作するようテストを修正した。
以前のレビュー修正時は認証直後のbusy中にF3を送った回帰テスト1件が失敗し、settings取得完了を待つよう修正した。今回のmain統合後は全件初回で合格。
最終実行はリトライ0回・失敗0件。E2E-058はinputs.screensに列挙した15画面を実際に訪問し完全一致を確認。
従来画面13種と移行追加の設定・サインオフを含む。DAVE本文・CALL先詳細本文はAPIにないため検査対象外。

`mvn -s maven-settings-gcs.xml -q test`:

```text
Tests run: 91, Failures: 0, Errors: 0, Skipped: 0
```

E2Eリセットの認証・設定反映・通常プロファイルでの無効化も含む。

`python3 -m unittest discover -s tests/tools -p 'test_*.py' -v`:

```text
Ran 6 tests in 0.002s

OK
```

Excel再生成後の独立検証:

```text
Excel: 58 cases, API + Web result groups (17 columns): PASS
Python/Java/Web fingerprints: 58/58 identical PASS
API cells: {'合格': 55, 'UI専用・フェーズ3/4': 3}
Web cells: {'合格': 58}
Current-run video evidence: 58/58 PASS; runId=6313614d-af3c-408e-9c69-7243de1fb088
```

## 視覚・動画確認

以前のレビュー修正時に投票エラー画面と非公開コメントの実画面、結合済み動画から抽出したフレームを目視確認した。今回のmain統合ではPlaywrightと録画再生成を実行し、追加の目視確認は行っていない。
賞の説明が連結して読みにくい状態と録画バナーのボタン重なりを修正し、58件を再実行した。

- `screenshots/E2E-003.png`: 投票の入力順、重複エラー、Fキー、賞の案内。
- `screenshots/E2E-027.png`: Name Hiddenと非公開本文の置換。
- `screenshots/E2E-058.png`: 番号付きメニュー。
- `screenshots/video-frame.png`: エンコード後のケースID・名称と操作画面。
- `screenshots/review-admin-F3.png` / `review-admin-signoff.png`: 管理者終了後は設定変更ボタンなし。失効tokenによるPATCHも401。
- `screenshots/review-first-page.png`: 先頭ページのF8無効。物理F8でresolve要求なし。
- `screenshots/review-learn-back.png`: EOF応答後にF8でPAGE 07へ戻り、F5を再有効化。

`npm run video` / ffprobe:

```text
codec_name: h264
width: 1280
height: 720
pix_fmt: yuv420p
duration: 94.160000
size: 4224373
```

`video/2_Web_React.mp4`へ58件をケース順に結合。100MB未満のため通常Gitへ格納。
結果の動画名はテスト直後は全件空欄。同じrunIdの58件合格録画を結合し終えた時のみ設定し、JSON/Markdown/Excelへ反映。
独立したtesting agentはこの環境では利用できず、上記はPlaywright直接実行とスクリーンショット確認の結果。
未実施: PostgreSQL実サーバー、IBM i実機、他ブラウザー、Windowsネイティブアプリ、Excel GUI。
