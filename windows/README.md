# VCF/400 Windows — .NET 8 WPF

Windows ネイティブの Visitor Studio。`frontend/src/App.tsx` の画面状態、REST 呼び出し、遷移を WPF に移植した。業務判定・必須チェック・画面表示可否・LEARN の分岐は既存 backend に任せる。グリーンスクリーンのピクセル模写ではない。

## 起動

Windows 10/11 または Windows Server の対話デスクトップ、.NET 8 SDK、Java 17、Maven を使用する。

```powershell
# リポジトリルート
dotnet build windows/VCF400.Windows.sln -c Release
dotnet run --project windows/src/VCF400.Windows -c Release --no-build
```

通常の backend は `backend/` で `mvn -s maven-settings-gcs.xml spring-boot:run` を実行する。管理者ログインを使う場合、起動前に `VCF_SEED_ADMPSWRD` に任意の非空・9文字以内の値を設定する。管理者入口は展示キオスクの Selection に `7` を入力する。

Git Bash でインストール済みツールが見つからない場合は新しい親プロセスからターミナルを開くか、以下を実行する（この環境のインストール先）。

```bash
export JAVA_HOME='/c/Program Files/Eclipse Adoptium/jdk-17.0.21.9-hotspot'
export PATH="$JAVA_HOME/bin:/c/Program Files/dotnet:/c/ProgramData/chocolatey/lib/maven/apache-maven-3.10.1/bin:$PATH"
```

## 設定・起動引数

API ベース URL の優先順位は **`--api` > 環境変数 `VCF_API_BASE` > 実行ファイルと同じディレクトリの `appsettings.json` の `ApiBase` > `http://127.0.0.1:8080`**。URL は `/api` を含まないサーバーのベース URL を指定する。認証情報を URL に含めない。HTTP はローカル専用。別ホストに接続する場合は HTTPS を使用する。

```powershell
dotnet run --project windows/src/VCF400.Windows -c Release --no-build -- --api http://127.0.0.1:8080 --collection global --page 8 --banner "E2E-038 CALL分岐と復帰" --width 1280 --height 800
```

- `--page NUMBER`: LEARN の指定ページを直接取得する。`--collection` は既定 `global`。ページを指定しなければメインメニューを開く（Web の URL クエリーに相当）。
- `--banner TEXT`: ケース ID・名称等の文字列を常時画面上部に表示する。AutomationId は `case-banner`。
- `--width` / `--height`: WPF の論理ピクセル単位。既定 `1280 × 800`、最小 `1280 × 720`、固定サイズ。高 DPI では実ピクセル数が変わる。画面全体を録画する場合は表示領域もこのサイズ以上にする。
- 不正な引数・設定は起動時のダイアログで通知する。引数や API 応答・認証情報のログ出力はしない。

## 操作・自動化インターフェース

- VCFMAIN の6グループとメニュー番号・無効項目を保持。展示一覧→展示別キオスク→投票/LEARN/投稿/閲覧、隠し7→管理者認証→投票受付変更、サインオフ、案内、完了画面に対応する。
- API の `screenTitle`、`fKeys` のラベル、`errorDisplay`（なければ `error`）は加工せず表示する。エラー行は空でも常設する。`error-row` の UI Automation `HelpText` に `errorPosition` の JSON（`row` / `column` / `length`、未指定時 `{}`）を保持する。
- F3/F5/F8/F12 は物理キーにも対応する。LEARN の先頭F8無効、末尾、終了、JUMP の backPage、CALL モーダルと復帰も Web と同じ。CALL 中は背景の操作を無効にする。
- 投票は Badge Number→Exhibit ID→Award ID、投稿は Your Name→Exhibit ID→Your Comment の順。Web 同様の最大長、数字欄の ASCII 数字のみ、キオスクの展示ID読み取り専用のみをクライアントで制約する。必須・投票適格性等は API に委譲する。
- 入力欄の Enter で送信できる（コメント欄は改行）。キオスク Selection の Enter または選択ボタンで画面を開く。
- 認証は `PasswordBox`、送信後は成功/失敗/通信失敗を問わずクリアする。token はプロセスメモリーのみ。管理者画面のF3・サインオフで `/admin/logout` を呼び、ローカルtokenも破棄する。ウィンドウ終了でも同様に破棄する。
- Web の `data-testid` と同名の `AutomationProperties.AutomationId` を設定。`entry-form` / `learn-card` / `completion` / `comment-card` / `exhibit-<ID>` は UIA Group として公開する。表示文字列は UIA Name/Text、入力欄は Value、PasswordBox は保護された入力として取得できる。E2E ランナーは今回の実装に含めない。

## Web 版との差異

画面は WPF のカード・固定サイドバー・スクロール領域で構成し、ブラウザー/DOM/URL履歴を使用しない。Fキーアクションは下部に固定する。追加の起動引数とネイティブウィンドウ終了処理を備える。多重リクエストによる状態競合を避けるため、通信中は画面操作を無効にする（Web は主に送信とFキーを抑止）。トークン・業務状態をディスクに保存しない。画面遷移の際にUIを再構成するため入力フォーカスは再設定される。

## E2E 用 backend

隔離したローカル H2 にのみ使用する。通常プロファイルや共有/本番 DB では有効にしない。

```bash
# backend/、Git Bash。値はログ表示・ファイル保存しない。
export VCF_SEED_ADMPSWRD="$(node -e 'process.stdout.write(require("crypto").randomBytes(5).toString("hex").slice(0,9))')"
export VCF_E2E_KEY="$(node -e 'process.stdout.write(require("crypto").randomBytes(32).toString("hex"))')"
mvn -s maven-settings-gcs.xml -q spring-boot:run \
  -Dspring-boot.run.profiles=e2e \
  -Dspring-boot.run.arguments=--server.port=8080
```

`/api/menu` の200だけでは初回seed完了を保証しない。ランナーは初回リセット前に、同じ `X-E2E-Key` を付けた `GET /api/e2e/counts` が baseline の `votes` / `comments` 件数（現行1/3）を返すまで期限付きで待つこと。件数の期待値は `tests/cases.json` の baseline から取得する。その後に `POST /api/e2e/reset`（JSON `{}`、同ヘッダー）を行う。通常のケース間リセットは順次実行する。起動直後の空DBでの `/counts` の200や固定sleepだけをreadiness判定にしない。

### 初回 reset と seed の競合（backend は未修正）

`BaselineSeeder` は `ApplicationRunner` で、`vcf.seed` は既定 true。`run()` は空DB判定→`populate()`を1トランザクションで実行し、`reset()` は全削除→同じ`populate()`を別トランザクションで実行する。両者の排他制御はない。`ApiController.menu()` はDBやseed状態を参照せずに200を返す。

実際に使用している Spring Boot 3.4.4 の `SpringApplication` バイトコードでは、コンテキスト起動→Startedログ→`callRunners()`→ready通知の順。したがってTomcatの起動ログやmenuの200は ApplicationRunner 完了より早い。初回seedとresetが並行すると同一の `EXHBDB.EXHUSRPRF` を挿入し、resetが負ければ `ApiErrors.conflict()` で409、seedが負ければ起動そのものが失敗する。

Windows/H2での調査ではmenuが200でもcountsは0/0だった。直後のresetが200となった一方、`main` とHTTPスレッドが同時に `EXHBDB` へINSERTし、`BaselineSeeder.run` が `EXH001` の主キー重複で失敗する経路をSQLログで再現した。別の新規起動で0/0→1/3を待ってからresetすると3回とも200だった。前回のreset側409もこの競合と整合するが、前回実行時のseed側SQLトレースはない。

今回の隔離・単一ランナーでは上記readiness待ちで回避でき、backend変更は必須ではない。複数クライアントからの同時resetや起動直後のreset自体を安全に受け付けたい場合には、backendにseed完了ゲートや共通ロック等が別途必要となる。今回は調査のみでbackendには変更していない。
