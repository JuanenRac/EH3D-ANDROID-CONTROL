<p align="center">
  <img src="images/EH3D_BANNER.svg" alt="EH3D-ANDROID-CONTROL banner" width="100%">
</p>

# 🤖 EH3D-ANDROID-CONTROL

<p align="center"><a href="README.md">🇺🇸 English</a> | <a href="README_spa.md">🇪🇸 Español</a> | <a href="README_fra.md">🇫🇷 Français</a> | <a href="README_ita.md">🇮🇹 Italiano</a> | <a href="README_deu.md">🇩🇪 Deutsch</a> | <a href="README_zho.md">🇨🇳 中文</a> | 🇯🇵 <b>日本語</b> | <a href="README_por.md">🇵🇹 Português</a></p>

### EH3D プログラムの Android アプリ：EH3D-SERVER を通じて工房の Bungard 機械をリアルタイムで追跡し、停止し、ジョブを 2D と 3D で表示し、ドリルヘッドのカメラを映します

<p align="center">
  <img src="https://img.shields.io/badge/License-GPL%203.0--or--later-blue.svg" alt="GPL 3.0 or later">
  <img src="https://img.shields.io/badge/Stack-Kotlin%20%2B%20Jetpack%20Compose-7F52FF.svg" alt="Kotlin, Jetpack Compose">
  <img src="https://img.shields.io/badge/Languages-8-00E5FF.svg" alt="Languages">
  <img src="https://img.shields.io/badge/Version-0.0.2-0f6cbd.svg" alt="Version">
  <img src="https://img.shields.io/badge/Maturity-scaffolding-FFB020.svg" alt="Maturity">
</p>

---

**正直な現状：** アプリのルール（EH3D-SERVER が機械・ジョブ・ヘッドについて返す内容の読み取り、完了ステップからのヘッド位置、2D と等角のジオメトリ、言語、アドレスの検査、セッションの保持）は書かれ、コンピューター上でテスト済みです。画面は EH3D プログラムの Forge テーマで Android 向けに書かれていますが、**このアプリは実際のサーバーに対して スマートフォン で実行されたことがなく**、緊急停止が実機を止めたことも、カメラが実際の基板を映したこともありません。

---

> **安全に関する状況：** このアプリは安全システムではなく、機械の非常停止の代わりにはなりません。停止はサーバー経由で実行中のジョブをキャンセルし、サーバーが機械を止めてドライバーをオフにします。ネットワークとサーバーが動作している必要があります。停止・開始できるのはオペレーターと管理者のみです。パスワードはローカルネットワークのアドレスか https にのみ送られ、セッションは暗号化されてスマートフォンに保持されます。


## 📋 ひと目で

| | |
|---|---|
| バージョン | `0.0.2` |
| 成熟度 | 足場 |
| テスト | JVM 上の単体テスト |
| 技術スタック | Kotlin, Jetpack Compose |
| 実機での動作 | まだです。アプリは スマートフォン でも、実サーバーに対しても、機械とも実行されていません。最初に試すのは、工房の EH3D-SERVER へのサインインです。 |

**移動先：** [概要](#-概要) · [アーキテクチャ](#-アーキテクチャ) · [クイックスタート](#-クイックスタート) · [テスト](#-テスト) · [構成](#-リポジトリの構成) · [関連](#-関連プロジェクト)

## 🚦 プロジェクトの状況

各行はこのリポジトリの一部分で、信号は今日どこまで進んでいるかを示します。計画ではなく、リポジトリが示せるもの（テスト、ビルド、実行）に基づいて書かれています。

🟢 完了・検証済み · 🟡 動作する（既知の制限あり） · 🟠 一部のみ、または未検証 · 🔴 未実装または動作しない

| 領域 | 状態 | ここでの意味 |
|---|---|---|
| ソフトウェア | 🟠 | 画面とアプリのルールは Android 向けに書かれています。ルールは端末なしでビルド・テストできます。 |
| テスト | 🟡 | JVM 上の単体テスト：サーバーが返す機械群・ジョブ・ヘッドの内容、ヘッドの位置、2D と等角のジオメトリ、言語、アドレスとバージョンの検査。 |
| 監視 | 🟠 | 工房の各機械の状態、ジョブ、進捗、完了ステップ、残り時間を 1.5 秒ごとにサーバーから読み取り、機械がオペレーターを待つか停止したときに通知します。実装済みですが、実サーバーでは未実行です。 |
| 緊急停止 | 🟠 | 2回確認してサーバー経由で実行中のジョブをキャンセルする赤いボタン（1台または全台）と、ジョブが待つ手順の確認。オペレーターと管理者のみ。実機を止めたことはなく、物理的な停止の代わりにはなりません。 |
| 2D / 3D ビューア | 🟠 | 基板、穴（穴あけ前はリング）、経路（切削前は薄く）、ヘッドを 2D と、指で回せる等角ビューで表示します。ヘッド位置は完了ステップからの推定です。ジオメトリとしてテスト済みですが、端末での表示は未確認です。 |
| ヘッドのカメラ | 🟠 | ドリルヘッド（EH3D-NODE-BUNGARD-AUX）のカメラを、サーバー経由の MJPEG で表示し、回転数・温度・保護出力、レーザーによるテーブル測定を行います。実装済みですが、実ノードでは未実行です。 |
| 更新 | 🟠 | GitHub でこのプロジェクトの最新リリースを確認し、APK をダウンロードして、パッケージ・バージョン・署名を検査したうえで Android のインストーラーに渡します。自分では決してインストールしません。実装・テスト済みですが、端末から GitHub に対しては未実行です。 |
| 実機での利用 | 🔴 | まだです。アプリは スマートフォン でも、実サーバーに対しても、機械とも実行されていません。最初に試すのは、工房の EH3D-SERVER へのサインインです。 |
| ドキュメント | 🟡 | 8 言語の README、8 言語のアプリ内ヘルプ、使用するサーバー API のメモ。 |

## 🎯 概要

* 工房の各機械の状態、ジョブ、進捗、完了ステップ、残り時間を 1.5 秒ごとにサーバーから読み取り、機械がオペレーターを待つか停止したときに通知します。実装済みですが、実サーバーでは未実行です。
* 2回確認してサーバー経由で実行中のジョブをキャンセルする赤いボタン（1台または全台）と、ジョブが待つ手順の確認。オペレーターと管理者のみ。実機を止めたことはなく、物理的な停止の代わりにはなりません。
* 基板、穴（穴あけ前はリング）、経路（切削前は薄く）、ヘッドを 2D と、指で回せる等角ビューで表示します。ヘッド位置は完了ステップからの推定です。ジオメトリとしてテスト済みですが、端末での表示は未確認です。
* ドリルヘッド（EH3D-NODE-BUNGARD-AUX）のカメラを、サーバー経由の MJPEG で表示し、回転数・温度・保護出力、レーザーによるテーブル測定を行います。実装済みですが、実ノードでは未実行です。
* GitHub でこのプロジェクトの最新リリースを確認し、APK をダウンロードして、パッケージ・バージョン・署名を検査したうえで Android のインストーラーに渡します。自分では決してインストールしません。実装・テスト済みですが、端末から GitHub に対しては未実行です。

機械のページには、ヘッドの LED リング（オフと 3 段階）と「品質チェック」カードもあります。サーバーにドリルヘッドのカメラで基板を見てもらい、穴とフライス溝を判定して、看板のような大きさで結果（合格・不合格・判定不可）と見つかった内容、プロファイル（標準・ファインピッチ・試作）、スケールの校正を表示します。最初の画面（マーク付きのスプラッシュ、セッション復元中の待機画面、アイコン・パスワード表示切替・端末に保存される内容の説明付きのサインイン）は、スイートの他のアプリの見た目に合わせています。

機械のページには、機械のスピンドル（オリジナル、またはインバーター付き）も表示され、その上限と、ノードに設定すべき最高回転数が分かります。管理者がスピンドルを選び、能力を超えるジョブをサーバーがどう扱うか（拒否する、またはスピンドルの上限まで下げて通す）を選びます。ジョブには、通したときの超過、無効にした工具、除外される完了済みの穴やパスが表示されます。このアプリはジョブを作成しないため、穴あけ・ルーター加工・両方の選択や工具の無効化は EH3D-STUDIO と BUNGARD-GUI で行います。

ドリルヘッドのカードには、センサーの状態も表示されます。レーザーの最後の測定の結果（有効、信号が弱い、ばらつきが大きい、範囲外、センサー故障）と距離、測定からの経過時間、レーザーを校正したときのオフセット、回転数センサーのパルスがきれいかノイズがあるか、ノイズとして除外されたパルスの数、ノードがワイヤー上で見つけた温度センサーの数（どれを読むかを推測しているだけのときは警告）、そしてノードのバージョンです。これらを伝えないノードでは何も表示されません。カメラ画像の設定、レーザーの校正、温度センサーの選択はノードのパネルで行います。サーバーはまだこれらを中継しません。

## 🧩 EH3D での役割

EH3D-ANDROID-CONTROL は EH3D 工房のポケットサイズの画面です。EH3D-SERVER とだけ通信し、サーバーが（EH3D-NODE-BUNGARD を通じて）機械でジョブを実行し、ドリルヘッド（EH3D-NODE-BUNGARD-AUX）のカメラを中継します。ジョブは EH3D-STUDIO または BUNGARD-GUI で準備し、このアプリはそれを追跡し、待機中の手順を確認し、停止します。

## 🏛️ アーキテクチャ

<p align="center">
  <img src="images/architecture.svg" alt="EH3D-ANDROID-CONTROL のアーキテクチャ：誰が使い、何を持ち、何に依存するか" width="100%">
</p>

このリポジトリの各部分の関係：誰が使い、何を持ち、何に依存するか。図は `docs/architecture.json` から描かれ、コードと照合されます。

## 🖼️ スクリーンショット

<p align="center">
  <img src="images/screenshots/app-home.png" alt="app-home" width="30%">
</p>

<p align="center"><em>工房の機械：空き・作業中・オペレーター待ちと、全機の停止ボタン。</em></p>

<p align="center">
  <img src="images/screenshots/app-machine.png" alt="app-machine" width="30%">
</p>

<p align="center"><em>1台の機械：停止ボタン、実行中のジョブ、ヘッド位置付きのライブ 2D ビュー。</em></p>

<p align="center">
  <img src="images/screenshots/app-viewer3d.png" alt="app-viewer3d" width="30%">
</p>

<p align="center"><em>同じジョブの等角ビュー（指で回転）と使用工具。</em></p>

<p align="center">
  <img src="images/screenshots/app-head.png" alt="app-head" width="30%">
</p>

<p align="center"><em>ドリルヘッド：サーバー経由のカメラ、回転数、温度、保護出力、テーブル測定。</em></p>

<p align="center">
  <img src="images/screenshots/app-jobs.png" alt="app-jobs" width="30%">
</p>

<p align="center"><em>サーバーのジョブと状態。</em></p>

<p align="center">
  <img src="images/screenshots/app-more.png" alt="app-more" width="30%">
</p>

<p align="center"><em>その他：セッション、GitHub からの更新、言語、ヘルプ。</em></p>

<p align="center"><em>同じルールとサンプルジョブで描いた画面のイラストで、端末のキャプチャではありません（アプリは端末で実行されていません）。</em></p>

## 🚀 クイックスタート

JDK 21 と Android SDK（残りは Gradle ラッパーが行います）、およびネットワーク上の EH3D-SERVER が必要です。

```bash
build.bat          # tests and debug APK (Windows)  -  ./build.sh on Linux
test.bat           # only the tests
python -I tools/ci_validate.py
```

アプリを開き、サーバーのアドレス（とポート 18200）を入力して「接続」を押し、サインインします。ユーザーのいないサーバーは、コンソールに表示されるセットアップコードを求め、最初の管理者を作成します。

## 🧪 テスト

`./gradlew test` は JVM 上で単体テストを実行します：サーバー JSON の読み取り（機械群・ジョブ・ヘッド・測定）、ヘッドの位置と2つのビューのジオメトリ、アドレスの検査、セッションの暗号化保存、更新のバージョンとリリースの検査、言語（ソースの全フレーズが7つの表に同じ穴付きで存在すること）。

## 🏗️ ビルドとリリース

バージョンは `eh3d.project.json` と `app/build.gradle.kts` にあり（異なるとビルドが止まります）、各バージョンは [CHANGELOG.md](CHANGELOG.md) に項目があります。リリース APK は R8 で縮小され、リポジトリの外に保管されたメンテナー自身の鍵で後から署名されます。リリースのアセットは `EH3D-ANDROID-CONTROL-release.apk` で、GitHub からの更新が探すファイルです。

## 📂 リポジトリの構成

<p align="center">
  <img src="images/repository-structure.svg" alt="EH3D-ANDROID-CONTROL のリポジトリ構成" width="100%">
</p>

<details>
<summary>ツリーのテキスト版</summary>

```text
EH3D-ANDROID-CONTROL/
├── app/
│   ├── build.gradle.kts proguard-rules.pro      ビルドと縮小ルール
│   ├── src/main/AndroidManifest.xml             権限と唯一のアクティビティ
│   ├── src/main/assets/i18n/                    8言語の文言
│   ├── src/main/java/es/electrohobby3d/eh3d/    アプリのコード
│   └── src/test/java/es/electrohobby3d/eh3d/    JVM 上のテスト
├── tools/
│   ├── ci_validate.py                           マニフェスト・バージョン・README の一致
│   └── make_architecture.py make_structure.py   README の2枚の図を描く
├── docs/                                        2枚の図のソース
├── gradle/                                      Gradle ラッパーのファイル
├── gradlew                                      Gradle ラッパー
├── gradlew.bat                                  Windows 用ラッパー
├── build.gradle.kts                             ビルドのプラグイン
├── settings.gradle.kts                          プロジェクト名
├── gradle.properties                            ビルドのメモリ
├── images/                                      バナーと画像
├── .github/                                     CI ワークフロー
├── build.bat build.sh                           ビルドとテスト実行
├── eh3d.project.json                            バージョン・役割・成熟度
├── CHANGELOG.md                                 各バージョンの変更点
├── SECURITY.md SUPPORT.md                       脆弱性の報告・質問
├── CONTRIBUTING.md                              貢献方法
├── CODE_OF_CONDUCT.md                           行動規範
└── LICENSE                                      ライセンス
```

</details>

## 🗺️ ロードマップと既知の制限

**現時点で分かっている制限**

* アプリは スマートフォン でも、実サーバーに対しても、機械とも実行されていません。
* ヘッドの位置は完了ステップからの推定で、サーバーは機械の実際の位置を報告しません。
* 緊急停止はサーバーとネットワークを経由します。物理的な停止ではありません。
* カメラは実際の映像を映したことがなく、テーブルの測定も実際のレーザーで行ったことがありません。

**次に**

* 工房の EH3D-SERVER にサインインして実際のジョブを追跡する。
* サーバーが報告するようになったら、ヘッドの実際の位置を表示する。
* アプリを閉じていても通知する（サーバーのプッシュチャネル）。
* スマートフォンからジョブを準備・開始するページ。

## 🌍 言語

README とテキストは、英語、スペイン語、フランス語、イタリア語、ドイツ語、中国語、日本語、ヨーロッパポルトガル語で提供されています。

## 🔗 関連プロジェクト

* [EH3D-SERVER](https://github.com/JuanenRac/EH3D-SERVER): EH3D ファミリーの中心：機械でジョブを実行し、このアプリが表示するすべてを提供します
* [EH3D-STUDIO](https://github.com/JuanenRac/EH3D-STUDIO): 機械を追加し、ジョブを準備するページ
* [EH3D-BUNGARD-GUI](https://github.com/JuanenRac/EH3D-BUNGARD-GUI): 穴あけとルーター加工のジョブを準備して実行するパソコン用プログラム
* [EH3D-NODE-BUNGARD-AUX](https://github.com/JuanenRac/EH3D-NODE-BUNGARD-AUX): ドリルヘッドのノード：このアプリが表示するカメラ、レーザー、センサー
* [EH3D-IOS-CONTROL](https://github.com/JuanenRac/EH3D-IOS-CONTROL): Android アプリと iPhone アプリは双子：同じルールと同じ画面

## 📚 ドキュメント

* [サーバーの API](https://github.com/JuanenRac/EH3D-SERVER/blob/main/docs/API.md)：このアプリが読み取り、要求する内容。
* [変更履歴](CHANGELOG.md)

## 🤝 コントリビュートとコミュニティ

* プルリクエストの前に `./gradlew test` と `python -I tools/ci_validate.py` を実行してください。どちらも成功する必要があります。[CONTRIBUTING.md](CONTRIBUTING.md) と[行動規範](CODE_OF_CONDUCT.md)を参照してください。
* アプリが表示するすべての文言は `app/src/main/assets/i18n` の表に8言語で入っており、テストが欠けを防ぎます。
* 脆弱性は非公開で報告してください：[SECURITY.md](SECURITY.md)。質問・提案・報告：electrohobby3d@gmail.com（[SUPPORT.md](SUPPORT.md)）。

## 👤 作者

**JuanenRac (Electro Hobby 3D)** · electrohobby3d@gmail.com

## 📜 ライセンス

ソフトウェア：GPL-3.0-or-later。[LICENSE](LICENSE) を参照。スイートのハードウェア設計には CERN OHL-S を使います。
