# VCF400 REST API

Java 17 / Spring Boot 3.4 / Maven / Spring Data JPA。既定はH2の実DB（PostgreSQL互換モード、プロセス終了時消去）。

## 起動・テスト

```sh
# backend/ で実行。管理者ログインを使う場合だけ任意の9文字以内の値を注入する。
# VCF_SEED_ADMPSWRD を未設定にするとADMPSWRD行を作らず、管理者ログインは無効。
mvn spring-boot:run
# 別端末: curl http://127.0.0.1:8080/api/menu
mvn -q test
# リポジトリルートで実行して結果列を更新:
python3 tests/tools/gen_cases.py
```

Maven Centralが429の場合は `mvn -s maven-settings-gcs.xml -q test`（GCSミラー）を使用できる。

既定は127.0.0.1バインド。公開する場合はTLS終端・認証試行レート制限・適切なプロキシを別途用意する。
フロントエンド開発時は同一オリジンの `/api` プロキシを使う（無制限CORSは有効化しない）。
H2をローカルファイルに保存する場合: `SPRING_DATASOURCE_URL='jdbc:h2:file:./data/vcf400;MODE=PostgreSQL' mvn spring-boot:run`。
初回の空DBにのみ `tests/cases.json` のbaselineを投入する。通常プロファイルでは既存DBの上書きリセット用HTTPエンドポイントは提供しない。
テストは `BaselineSeeder.reset` をJava内部から呼び、毎ケース実DBを初期化する。
Web E2Eでは`e2e`プロファイル限定・非空`VCF_E2E_KEY`認証付きの`/api/e2e/reset`と`/api/e2e/counts`を使う。
これは隔離されたローカルH2用であり、本番・共有DBで有効にしない。起動・録画手順は`frontend/README.md`を参照。

## PostgreSQL

```sh
# すべて接続先環境から注入（値をGit管理しない）
SPRING_PROFILES_ACTIVE=postgres mvn spring-boot:run
```

必須環境変数: `DATABASE_URL`（jdbc:postgresql形式）、`DATABASE_USERNAME`、`DATABASE_PASSWORD`。
postgresプロファイルはseed無効・DDL validate。初期スキーマは運用側で準備する。
開発用の新規DBに限り `SPRING_JPA_HIBERNATE_DDL_AUTO=update` でJPAから作成できる。
`VCF_SEED=true` を明示した空DBでのみbaseline投入も可能。管理者値はそのとき `VCF_SEED_ADMPSWRD` から注入する。
PostgreSQL実サーバーとの接続検証は未実施。

## JSON API（すべて `/api` 配下）

| メソッド | パス | 用途 |
|---|---|---|
| GET | /menu | VCFMAINの階層・番号・原文タイトル・実装有無、案内画面メタデータ |
| GET | /exhibits | 展示一覧 |
| GET | /vote?profile=MM2024 | 投票画面情報。ALWVOTE=NならENDOFCONへスキップ |
| POST | /vote?profile=MM2024 | `{ "badge": 1002, "exhibit": "EXH001", "award": 1 }` |
| GET | /guestbook/form?profile=MM2024 | 投稿画面情報・展示固定情報 |
| POST | /guestbook?profile=MM2024 | `{ "name": "Visitor", "exhibit": "EXH001", "comment": "Hello" }` |
| GET | /guestbook/{id}?profile=MM2024 | ID以上で最初のコメント、末尾超過は最終レコード |
| GET | /learn/{collection} | 初期ページ。collectionはglobalまたは展示ID |
| GET | /learn/{collection}/pages/{page} | ページ単純取得（分岐は実行しない） |
| POST | /learn/{collection}/resolve | `{ "from": 2, "action": "F5", "backPage": 0 }`。F3/F5/F8を解決 |
| GET | /kiosk/{profile} | 展示キオスクの表示可能番号、隠し7の契約 |
| POST | /admin/auth | `{ "password": "環境変数で注入した値" }`。正しい場合のみtokenを返す |
| POST | /admin/logout | Bearer認証ヘッダーは任意。指定したtokenだけを失効し、200 `{ "loggedOut": true }` を返す |
| GET | /settings | ALWVOTEのみ。ADMPSWRDやハッシュは絶対に返さない |
| PATCH | /settings | `Authorization: Bearer <token>` と `{ "ALWVOTE": "N" }` |

認証済みPATCH /settingsには任意でADMPSWRD（非空白・9文字以内）も指定できる。
新値はハッシュ化して保存し、全tokenを失効させる。レスポンスには旧値・新値・ハッシュのいずれも返さない。

業務エラー（必須・投票4チェック）はHTTP 200の `saved:false` と原文 `error`。
`errorPosition` に旧画面の行・列・長さ、`errorDisplay` に画面幅で切詰めた表示を返す。
型・桁数・範囲・未知フィールドは400、ページ/展示欠損は404、認証不足は401、登録競合は409。
数値文字列・小数を整数へ暗黙変換しない。タイトルは `screenTitle`、Fキーは `fKeys`、入力順は `inputOrder`。
`profile` は展示端末コンテキストであり管理者認証ではない。MM2024でも設定更新には必ずtokenが必要。
VISIBLE=Nはすべてのprofileで本文・名前を置換。profileによる別展示制限は従来の表示ルールで、強いユーザー認証境界ではない。

## LEARNと原典との差異

- EXTRA=END、CONTENT=CALL/JUMP。CALL/JUMPはEND判定より優先。初期取得は分岐しない。
- CALLは任意のサーバープログラムを実行せず、許可済みCREDITS/NTRSTITの `callScreen` と復帰先を返す。UIはcallScreenを表示後に `pageAfterReturn` へ戻る。
- JUMP先で連鎖分岐はしない。分岐後のF8は応答のbackPageを送る。
- 原典LRN400STUBのファイル単位オーバーライドは、LRN400STRの追加列COLLECTIONと複合主キーで保持。
- SETTINGS.VALUEはハッシュ格納のため9文字から512文字に拡張。その他の旧列名を大文字のまま保持。ADMPSWRDはPBKDF2-HMAC-SHA256（210000回、ランダムsalt）。
- 認証tokenはランダム256bit、有効期限10分、メモリー保持。再起動・テストリセットで失効する。クラスタ間共有は未実装。
- 空ゲストブック/学習ページはempty=true。不正JUMP引数は400、欠損先は404。これらは原典未測定の防御的方針。
- コメントIDはmax+1、空DBでは1。単一インスタンス内は直列化し、DB一意制約とinsertで既存行上書きを防ぐ。他インスタンスと競合した場合は409で再試行を要求。
- 投稿で展示存在を追加検証しない（原典準拠）。投票の存在しない展示に対するSETLL後の適格性参照は安全に失敗として扱い、後段の展示不存在エラーで上書きする。

## 結果

共通58ケースをJSONから読み込み、API該当ケースをMockMvc経由でコントローラー→JPA→H2まで実行。
F12取消2件と全画面配置照合1件は「UI専用・フェーズ3/4」。APIのメニュー・画面メタデータは別テストで照合。
`test-results/api_e2e_results.json` / `.md` に各ケースの期待値・実際値・合否を記録する（token・パスワードは記録しない）。
JUnitの詳細件数はMaven出力および `target/surefire-reports` を参照。
本リポジトリにはJava lint/typecheck専用ステージなし。Maven testでコンパイルと全JUnitを実行する。
