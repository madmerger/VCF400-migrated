# フェーズ3 Web検証

環境: Linux / Node.js 24.22.2 / React 19 / TypeScript 5 / Vite 7.3.8 / Playwright 1.65.1 / Chromium。
バックエンドは別プロセスのSpring Boot 3.4.4 + 実H2。Viteプロキシを通して実HTTP通信した。

## 実行結果

`npm run build`（strict TypeScript検査を含む）:

```text
✓ 29 modules transformed.
✓ built in 729ms
```

独立したlintスクリプトは未設定。

`npm run test:e2e`:

```text
58 passed (1.1m)
```

58ケースすべてUIで実行。F12取消2件と画面台帳照合も合格。
入力・画面表示・キー操作に加え、各ケース前後の実DB件数を確認した。
管理者認証成功ケースでは投票受付変更と投票画面スキップまで操作した。
通信失敗時の表示・復帰も画面台帳ケースで確認した。
初回にLEARNの初期データ受信前に物理キーを送って4件失敗したため、UIのbusy解除を待ってから操作するようテストを修正した。
最終実行はリトライ0回・失敗0件。

`mvn -s maven-settings-gcs.xml -q test`:

```text
Tests run: 85, Failures: 0, Errors: 0, Skipped: 0
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
```

## 視覚・動画確認

投票エラー画面と非公開コメントの実画面、結合済み動画から抽出したフレームを目視確認した。
賞の説明が連結して読みにくい状態と録画バナーのボタン重なりを修正し、58件を再実行した。

- `screenshots/E2E-003.png`: 投票の入力順、重複エラー、Fキー、賞の案内。
- `screenshots/E2E-027.png`: Name Hiddenと非公開本文の置換。
- `screenshots/E2E-058.png`: 番号付きメニュー。
- `screenshots/video-frame.png`: エンコード後のケースID・名称と操作画面。

`npm run video` / ffprobe:

```text
codec_name: h264
width: 1280
height: 720
pix_fmt: yuv420p
duration: 90.520000
size: 4083984
```

`video/2_Web_React.mp4`へ58件をケース順に結合。100MB未満のため通常Gitへ格納。
独立したtesting agentはこの環境では利用できず、上記はPlaywright直接実行とスクリーンショット確認の結果。
未実施: PostgreSQL実サーバー、IBM i実機、他ブラウザー、Windowsネイティブアプリ、Excel GUI。
