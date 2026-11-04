# iPad など別の機器から開く手順（面接用のメモ）

パソコンで動かしているアプリを、同じネットワークにつないだ iPad の Safari から開くための手順です。面接で、iPad を触ってもらうときに使います。

- これは**作者が面接の準備で使うメモ**です。アプリを使うだけなら、[README の起動方法](../README.md#起動方法) だけで足ります。
- **インターネットへの公開ではありません。** 同じネットワークの中の機器だけが開けるようにします。
- **IP アドレス、ネットワークの名前、パスワードの値は、この文書に書きません。** 調べ方だけを書きます。
- 実際に iPad で確かめた記録は、[Phase 8 の手順書 4.13](plans/phase-8-polish.md) にあります。

## 先に知っておくこと

- アプリの通信は **HTTP（暗号化されない）** です。iPad の Safari に「保護されていない通信」と出ますが、正常です。
- そのため、**iPad で使うアカウントのパスワードは、このアプリ専用のもの**にします（ほかのサービスと同じものにしない）。
- ファイアウォール（外からの通信を止める Windows の仕組み）の設定には、**管理者の PowerShell** が要ります。管理者として開くには、スタートメニューで「PowerShell」を右クリックして「管理者として実行」を選びます。
- 手順 2〜4 でパソコンの設定を変えます。**終わったら、必ず手順 7 で元に戻します。**

## 手順

### 1. パソコンと iPad を、同じネットワークにつなぐ

- 自宅の Wi-Fi なら、両方を同じ Wi-Fi につなぎます。
- **面接の会場では、スマホのテザリング（インターネット共有）を使います。** パソコンと iPad の**両方**を、そのスマホの Wi-Fi につなぎます。
  - **会場やカフェの Wi-Fi は使いません。** 理由は 2 つあります。①つないだ機器どうしの通信を禁止していること（ゲスト用など）があり、その場合は IP アドレスで開けません。②手順 2 で `Private` にすると、同じ Wi-Fi につないでいる知らない人から、パソコンが見えやすくなって危険です。自分のスマホのテザリングなら、つないでいるのは自分の機器だけなので、安全に使えます。
  - アプリはパソコンの中で動くので、スマホのデータ通信量は、ほとんど増えません。

### 2. ネットワークの種類を `Private` にする

Windows は、新しくつないだネットワークを「パブリック」にしがちです（テザリングは特に）。パブリックのままだと、手順 4 の許可が効かず、iPad から開けません。

1. 今の種類を調べます。

   ```powershell
   Get-NetConnectionProfile
   ```

   iPad をつなぐ側のネットワーク（手順 1 でつないだもの）の `InterfaceAlias`（アダプターの名前）と `NetworkCategory`（`Private` か `Public`）を読みます。**変える前の `NetworkCategory` を、メモしておきます**（手順 7 で戻すときに使います）。

2. `Public` だったら、`Private` にします（管理者の PowerShell）。

   ```powershell
   Set-NetConnectionProfile -InterfaceAlias "（1 で読んだ InterfaceAlias）" -NetworkCategory Private
   ```

   もう一度 `Get-NetConnectionProfile` を実行して、`Private` になったことを確かめます。

**`Private` にしてよいのは、自分のスマホのテザリングと、自宅の Wi-Fi だけです。会場やカフェなど、知らない人もつなぐ Wi-Fi は、`Private` にしません。** `Private` にすると、そのネットワークの中の機器から、パソコンが見えやすくなるためです。

もともと `Private` だったネットワーク（自宅の Wi-Fi など）は、触りません。

### 3. パソコンの IP アドレスを調べる

```powershell
ipconfig
```

手順 1 でつないだ Wi-Fi のアダプターの「IPv4 アドレス」を読みます（`192.168.…` のような形）。**値は手元に控えるだけで、文書やチャットには書きません。**

### 4. アプリを起動して、待ち受けを確かめる

1. [README の起動方法](../README.md#起動方法) のとおり、アプリを起動します（`.\mvnw spring-boot:run`）。
   - **「Java をファイアウォールで許可しますか」のようなダイアログが出たら、「キャンセル」（許可しない）を選びます。** 「許可」を押すと、Java 全体を許す広いルールができてしまいます。必要な許可は、手順 5 で、8080 番だけに絞って作ります。
2. 別のターミナルで、次を実行します。

   ```powershell
   netstat -an | findstr :8080
   ```

   `0.0.0.0:8080`（または `[::]:8080`）が `LISTENING` なら、パソコンの外から開ける状態です。`127.0.0.1:8080` だけのときは、外から開けません。

3. パソコンのブラウザで `http://（パソコンの IP アドレス）:8080/` を開いて、ログイン画面が出ることを確かめます。ここで開けないときは、iPad でも開けません。

### 5. ファイアウォールで 8080 番を、必要な範囲だけ許可する

管理者の PowerShell で、次を実行します。

```powershell
New-NetFirewallRule -DisplayName "HouseholdBudget-8080-temp" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow -Profile Private -RemoteAddress LocalSubnet
```

- `-Profile Private`：`Private` のネットワークだけ。
- `-RemoteAddress LocalSubnet`：同じネットワークの中の機器だけ（外からは開けない）。
- 作れたか確かめます。`Enabled` が `True`、`Direction` が `Inbound`、`Action` が `Allow`、`Profile` が `Private` なら正しい形です。

  ```powershell
  Get-NetFirewallRule -DisplayName "HouseholdBudget-8080-temp" | Format-Table DisplayName, Enabled, Direction, Action, Profile
  ```

- 手順 4 のダイアログで、うっかり「許可」を押したときは、手順 7 の 3 で、広いルールができていないか確かめます。

### 6. iPad の Safari で開く

1. Safari のアドレス欄に `http://（パソコンの IP アドレス）:8080/` を入力します。
2. ログイン画面が出たら成功です。ログインして、使います。

### 7. 終わったら、元に戻す（必ず行う）

管理者の PowerShell で、上から順に行います。

1. アプリを止めます（起動したターミナルで `Ctrl+C`）。
2. 手順 5 の許可を消します。

   ```powershell
   Remove-NetFirewallRule -DisplayName "HouseholdBudget-8080-temp"
   ```

   消えたかを確かめます。「見つかりません」というエラーが出れば、消えています。

   ```powershell
   Get-NetFirewallRule -DisplayName "HouseholdBudget-8080-temp"
   ```

3. **Java の広い許可ルールが残っていないか確かめます。**

   ```powershell
   Get-NetFirewallRule -Direction Inbound -Enabled True | Where-Object { $_.DisplayName -like "*java*" -or $_.DisplayName -like "*OpenJDK*" } | Format-Table DisplayName, Profile, Action
   ```

   何も出なければ問題ありません。出たときは、**内容を見てから**扱いを決めます。ほかの Java のアプリが使っているかもしれないので、消さずに無効にする（`Disable-NetFirewallRule`）方が安全です。戻すときは `Enable-NetFirewallRule` を使います。
4. ネットワークの種類を、**手順 2 で控えた種類に戻します。** 変える前が `Public` だったネットワークは、必ず `Public` に戻します（`Private` のままだと、そのネットワークで、パソコンがほかの機器から見えやすい状態になります。信頼できない会場のネットワークでは特に危険です）。

   ```powershell
   Set-NetConnectionProfile -InterfaceAlias "（手順 2 で読んだ InterfaceAlias）" -NetworkCategory Public
   ```

   `Get-NetConnectionProfile` で、戻ったことを確かめます。
5. スマホのテザリングを切ります。

## うまくいかないとき

| 症状 | 考えられる原因 | 確かめること |
|---|---|---|
| iPad で開けない（つながらない、時間切れになる） | 同じネットワークにつないでいない／会場の Wi-Fi が機器どうしの通信を禁止している／許可がない／アプリが起動していない／IP アドレスが違う | まず、パソコン自身から IP アドレスで開けるか（手順 4 の 3）。次に、ネットワークが `Private` か（手順 2）、許可ルールがあるか（手順 5）。会場の Wi-Fi が原因らしいときは、スマホのテザリングにする |
| パソコン自身から IP アドレスで開けない | IP アドレスが違う／アプリが外からの接続を待っていない | `ipconfig` で IP アドレスを読み直す。`netstat -an \| findstr :8080` で `0.0.0.0:8080` を確かめる |
| 許可ルールを作ったのに、iPad から開けない | ネットワークが `Public` のまま | `Get-NetConnectionProfile` で確かめて、`Private` にする（手順 2）。変えられないときは、許可ルールの `-Profile` を、そのネットワークの種類に合わせて作り直す（`-RemoteAddress LocalSubnet` は残す） |
| テザリングにつないだのに、パソコンと iPad が通信できない | スマホが、つないだ機器どうしの通信を許可していない | スマホの設定を見る。だめなら、別のスマホにするか、パソコンだけで見せる |
| ログイン画面は出るが、ログインしても戻される | Cookie が保存されていない（Safari のプライベートブラウズ、Cookie をブロックする設定） | Safari の設定を見る。通常のタブで開く |
| 「保護されていない通信」と出る | HTTP で開いているため | 正常です |
| 許可ルールを消したのに、iPad から開ける | ほかの広いルール（Java のダイアログで許可したもの）が残っている | 手順 7 の 3 のコマンドで確かめて、内容を見てから扱う |
