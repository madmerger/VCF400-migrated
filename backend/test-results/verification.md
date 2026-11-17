# フェーズ2 検証サマリー

環境: Java 17.0.20.1 / Maven 3.9.9 / Spring Boot 3.4.4 / H2 2.3.232 / Linux。

実行コマンド（backend/）:

```sh
mvn -s maven-settings-gcs.xml -q test
```

通常の `mvn -q test` は Maven CentralのHTTP 429で親POM取得が失敗したため、指定どおりGCSミラーを使用。
初回JUnitでは数値→文字列の暗黙変換と、F3操作の省略fromフィールドで2件失敗した。
文字列型への数値・真偽値変換を禁止し、F3以外でのみfromを必須にして修正した。

最終JUnit出力（Surefire XMLから抽出）:

```text
org.vcf400.AdminDisabledTest: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
org.vcf400.AdminSecurityTest: Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
org.vcf400.ApiCasesTest: Tests run: 58, Failures: 0, Errors: 0, Skipped: 0
org.vcf400.ApiSecurityTest: Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
Tests run: 72, Failures: 0, Errors: 0, Skipped: 0
API共通ケース: 全58件 / 合格55 / 不合格0 / UI専用3
```

JUnitの58件にはUI専用として分類する3件も含む（JUnit上のSkippedではなく、レポート上の明示分類）。
UI専用: E2E-015、E2E-024（F12取消）、E2E-058（全画面台帳照合）。これらをAPI合格数には算入しない。
独立した14件で認証なし・不正token、token更新/失効/期限、パスワード未設定、ハッシュ格納、
型・長さ・範囲検証、認証応答の秘匿、CALL許可リスト、メニューとメタデータ等を検証した。

Excel・JSON再生成と構造検証:

```text
画面トレーサビリティ: 58ケースのL-IDが対象画面と完全一致、LEARN/400へのVCFMAIN混入0件: PASS
ケース生成: tests/VCF400_e2e_cases.xlsx / tests/cases.json (58件、API結果58件反映)
Excel: 58ケース・結果5列・全参照ID・環境・動画空欄: PASS {'合格': 55, 'UI専用・フェーズ3/4': 3}
管理者シード既定値リテラル（ソース・JSON・Excel）: 残存0件 PASS
```

安全性自己点検:

- ADMPSWRDは環境変数注入のみ。未設定時は管理者行を作成しない。テストはランダム値を実行時生成する。
- DBではPBKDF2-HMAC-SHA256、salt付き・210000回。比較はMessageDigest.isEqual。
- 生パスワード・ハッシュをAPI応答/共通ケースレポートへ返さない。MockMvcリクエスト自動出力を無効化。
- 管理者更新に10分期限のランダムtoken必須。パスワード変更はtokenを全失効。
- 入力はBean ValidationとJacksonの厳格型検証。認証/解析エラー応答に入力値や例外を埋め込まない。
- 任意プログラム実行なし。CALLはCREDITS/NTRSTITの画面・復帰先を宣言的に返す。
- 本番TLS、レート制限、複数ノードtoken共有は運用側の追加作業。既定はループバックのみ。

未実施: PostgreSQL実サーバー、IBM i実機、Web/Windows操作・録画、Excel GUI目視。
APIはMockMvcからHTTPディスパッチ→サービス→JPA→H2まで実行。外部TCP経由の検証ではない。
既存Java lint/typecheck集約スクリプトはなく、Maven testでJavaコンパイルと全JUnitを実行。
