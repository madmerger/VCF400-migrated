# API E2E結果

環境: API (Spring Boot/H2) / MockMvc（HTTPコントローラー・JPA・実H2）

全58件 / 合格 55 / 不合格 0 / UI専用 3

| ケースID | 名称 | 合否 | 期待値 | 実際値 |
|---|---|---|---|---|
| E2E-001 | 正常投票・Best in Show | 合格 | {"saved":true,"screen":"VOTEEND"} | {"saved":true,"screen":"VOTEEND"} |
| E2E-002 | 正常投票・Ed Fair | 合格 | {"saved":true,"screen":"VOTEEND"} | {"saved":true,"screen":"VOTEEND"} |
| E2E-003 | 重複バッジ・別賞でも拒否 | 合格 | {"saved":false,"error":"You have already voted."} | {"saved":false,"error":"You have already voted."} |
| E2E-004 | 不適格展示 | 合格 | {"saved":false,"error":"Exhibit ineligible for award"} | {"saved":false,"error":"Exhibit ineligible for award"} |
| E2E-005 | 存在しない展示 | 合格 | {"saved":false,"error":"Exhibit does not exist"} | {"saved":false,"error":"Exhibit does not exist"} |
| E2E-006 | 存在しない賞 | 合格 | {"saved":false,"error":"Award does not exist."} | {"saved":false,"error":"Award does not exist."} |
| E2E-007 | 全チェック失敗・最後のエラー優先 | 合格 | {"saved":false,"error":"Award does not exist."} | {"saved":false,"error":"Award does not exist."} |
| E2E-008 | 重複と不適格・適格性エラー優先 | 合格 | {"saved":false,"error":"Exhibit ineligible for award"} | {"saved":false,"error":"Exhibit ineligible for award"} |
| E2E-009 | 投票期間終了・画面スキップ | 合格 | {"saved":false,"screen":"ENDOFCON","voteFormVisible":false} | {"saved":false,"screen":"ENDOFCON","voteFormVisible":false} |
| E2E-010 | バッジ未入力 | 合格 | {"saved":false,"error":"Must enter badge number"} | {"saved":false,"error":"Must enter badge number"} |
| E2E-011 | 展示未入力 | 合格 | {"saved":false,"error":"Must enter Exhibit ID"} | {"saved":false,"error":"Must enter Exhibit ID"} |
| E2E-012 | 賞未入力 | 合格 | {"saved":false,"error":"Must enter Award ID"} | {"saved":false,"error":"Must enter Award ID"} |
| E2E-013 | 全項目未入力 | 合格 | {"saved":false,"error":"Must enter Exhibit ID"} | {"saved":false,"error":"Must enter Exhibit ID"} |
| E2E-014 | キオスク展示固定 | 合格 | {"exhibit":"EXH001","exhibitEditable":false} | {"exhibit":"EXH001","exhibitEditable":false} |
| E2E-015 | F12取消・書込みなし | UI専用・フェーズ3/4 | {"saved":false,"returned":true} | {} |
| E2E-016 | DAVE隠し画面 | 合格 | {"screen":"DAVE","saved":false} | {"screen":"DAVE","saved":false} |
| E2E-017 | 正常登録・max+1 | 合格 | {"saved":true,"CMTID":6,"VISIBLE":"Y","screen":"ENDCMT"} | {"saved":true,"CMTID":6,"VISIBLE":"Y","screen":"ENDCMT"} |
| E2E-018 | 空DBへの初回登録 | 合格 | {"saved":true,"CMTID":1,"VISIBLE":"Y"} | {"saved":true,"CMTID":1,"VISIBLE":"Y"} |
| E2E-019 | name未入力 | 合格 | {"saved":false,"errorDisplay":"Must enter your name"} | {"saved":false,"errorDisplay":"Must enter your name"} |
| E2E-020 | exhibit未入力 | 合格 | {"saved":false,"errorDisplay":"Must enter Exhibit ID"} | {"saved":false,"errorDisplay":"Must enter Exhibit ID"} |
| E2E-021 | comment未入力 | 合格 | {"saved":false,"errorDisplay":"Must enter a comment"} | {"saved":false,"errorDisplay":"Must enter a comment"} |
| E2E-022 | 全項目未入力・最終エラー | 合格 | {"saved":false,"errorDisplay":"Must enter a comment"} | {"saved":false,"errorDisplay":"Must enter a comment"} |
| E2E-023 | 存在しない展示も原典は登録 | 合格 | {"saved":true,"CMTID":6} | {"saved":true,"CMTID":6} |
| E2E-024 | F12取消 | UI専用・フェーズ3/4 | {"saved":false,"returned":true} | {} |
| E2E-025 | キオスク展示固定 | 合格 | {"exhibit":"EXH001","exhibitEditable":false} | {"exhibit":"EXH001","exhibitEditable":false} |
| E2E-026 | 通常閲覧 | 合格 | {"name":"Alice","comment":"Great exhibit","title":"展示一"} | {"name":"Alice","comment":"Great exhibit","title":"展示一"} |
| E2E-027 | 非表示は名前・本文を置換 | 合格 | {"name":"Name Hidden","comment":"This comment hidden by an admin - offensive content."} | {"name":"Name Hidden","comment":"This comment hidden by an admin - offensive content."} |
| E2E-028 | キオスクから別展示は非公開 | 合格 | {"name":"","title":"","comment":"This comment is not part of this guestbook."} | {"name":"","title":"","comment":"This comment is not part of this guestbook."} |
| E2E-029 | 同展示は公開 | 合格 | {"name":"Alice","comment":"Great exhibit"} | {"name":"Alice","comment":"Great exhibit"} |
| E2E-030 | 末尾超過は最終レコード | 合格 | {"CMTID":5,"name":"Carol","comment":"Last comment"} | {"CMTID":5,"name":"Carol","comment":"Last comment"} |
| E2E-031 | 欠番は次のID | 合格 | {"CMTID":5,"name":"Carol"} | {"CMTID":5,"name":"Carol"} |
| E2E-032 | ID未入力 | 合格 | {"error":"Must enter CommentID"} | {"error":"Must enter CommentID"} |
| E2E-033 | 表示件数は最終ID | 合格 | {"displayedTotal":5} | {"displayedTotal":5} |
| E2E-034 | 空DB閲覧 | 合格 | {"empty":true,"note":"移行API方針。原典未測定、例外や他レコード漏洩なし"} | {"empty":true} |
| E2E-035 | 最初のページ | 合格 | {"page":1,"content":"開始"} | {"page":1,"content":"開始"} |
| E2E-036 | 通常前進 | 合格 | {"page":2,"content":"通常ページ"} | {"page":2,"content":"通常ページ"} |
| E2E-037 | JUMP分岐 | 合格 | {"action":"JUMP","page":5,"content":"移動先","backPage":2} | {"action":"JUMP","page":5,"content":"移動先","backPage":2} |
| E2E-038 | CALL分岐と復帰 | 合格 | {"action":"CALL","program":"CREDITS","pageAfterReturn":7,"backPage":5} | {"action":"CALL","program":"CREDITS","pageAfterReturn":7,"backPage":5} |
| E2E-039 | ENDで終了 | 合格 | {"ended":true} | {"ended":true} |
| E2E-040 | 通常戻る | 合格 | {"page":1} | {"page":1} |
| E2E-041 | JUMP後戻る | 合格 | {"page":2} | {"page":2} |
| E2E-042 | CALL後戻る | 合格 | {"page":5} | {"page":5} |
| E2E-043 | 末尾前進 | 合格 | {"content":"","allowForward":false} | {"content":"","allowForward":false} |
| E2E-044 | F3終了 | 合格 | {"ended":true} | {"ended":true} |
| E2E-045 | 展示別ページ | 合格 | {"page":1,"content":"開始","collection":"EXH001"} | {"page":1,"content":"開始","collection":"EXH001"} |
| E2E-046 | ページなし | 合格 | {"empty":true,"note":"移行API方針。原典未測定"} | {"empty":true} |
| E2E-047 | 不正JUMP引数 | 合格 | {"validationError":true,"note":"移行API方針。原典未測定"} | {"validationError":true} |
| E2E-048 | 正しいパスワードで終了 | 合格 | {"exited":true} | {"exited":true} |
| E2E-049 | 誤ったパスワード | 合格 | {"exited":false,"customError":null} | {"exited":false,"customError":null} |
| E2E-050 | 空パスワード | 合格 | {"exited":false} | {"exited":false} |
| E2E-051 | 全項目表示・隠し7は非表示 | 合格 | {"visibleOptions":[1,2,3,4],"hiddenOptions":[7],"title":"WELCOME TO..."} | {"visibleOptions":[1,2,3,4],"hiddenOptions":[7],"title":"WELCOME TO..."} |
| E2E-052 | 不適格・学習無効 | 合格 | {"visibleOptions":[3,4],"blockedOptions":[1,2]} | {"visibleOptions":[3,4],"blockedOptions":[1,2]} |
| E2E-053 | 展示データ一覧 | 合格 | {"ids":["EXH001","EXH002"],"count":2} | {"ids":["EXH001","EXH002"],"count":2} |
| E2E-054 | 空一覧 | 合格 | {"ids":[],"count":0} | {"ids":[],"count":0} |
| E2E-055 | 投票設定参照 | 合格 | {"ALWVOTE":"Y","passwordNotExposed":true} | {"ALWVOTE":"Y","passwordNotExposed":true} |
| E2E-056 | 認証成功 | 合格 | {"authenticated":true} | {"authenticated":true} |
| E2E-057 | 認証失敗 | 合格 | {"authenticated":false,"passwordNotExposed":true} | {"authenticated":false,"passwordNotExposed":true} |
| E2E-058 | メニュー番号・原文タイトル・Fキー・エラー位置照合 | UI専用・フェーズ3/4 | {"matchesAllLedgerElements":true} | {} |
