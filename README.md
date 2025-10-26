航海日誌 (logbook-kai)
--
[![GitHub release (latest by date)](https://img.shields.io/github/v/release/nomonomo/logbook-kai)](https://github.com/nomonomo/logbook-kai/releases/latest)
[![GitHub](https://img.shields.io/github/license/nomonomo/logbook-kai)](LICENSE)
[![GitHub All Releases](https://img.shields.io/github/downloads/nomonomo/logbook-kai/total)](https://github.com/nomonomo/logbook-kai/releases)
[![GitHub Release Date](https://img.shields.io/github/release-date/nomonomo/logbook-kai)](https://github.com/nomonomo/logbook-kai/releases)

## ****重要なお知らせ****

セキュア化対応を行いましたが、実装内容があやしいので、**自己責任でお願いします**

**現状はWindowsのみ対応**

### その１：証明書のインストールが必要です

艦これのHTTPS化に伴い、ブラウザまたはOSにルート証明書のインストールが必要になります。

**詳細な手順については、[ブラウザの設定(必須)](how-to-preference.md)を参照してください。**

### その２：起動方法について

jlinkを利用して実行専用のJavaランタイムイメージを作成しています。

これにより、**Javaの事前インストールが不要**となりました。

**起動方法：**
配布フォルダ内の `launch.bat` をダブルクリックして起動してください。

### その３：他の航海日誌改からの移行・アップデート方法

既に航海日誌改を使用している方は、以下の手順でアップデートできます。

**手順：**
1. ダウンロードしたZIPファイルを展開する
2. 展開したフォルダ内の `logbook` フォルダと `launch.bat` を、既存の航海日誌フォルダにコピー（上書き）する
3. `launch.bat` を実行する

**注意：** 設定ファイルやデータファイルはそのまま引き継がれます。

### その４：バージョン 25.10.1～25.10.3 をお使いだった方へ

証明書作成方法が変更されました。

**変更点：**
- `create-ca-and-cert.bat` の配布を終了しました
- 航海日誌の `[設定] - [通信]` 画面から証明書を作成できるようになりました

**既存の証明書について：**
以前のバージョンで作成した証明書（`logbook-ca.crt`、`kancolle.p12` など）は、**そのまま使用できます**。新たに証明書を作成する必要はありません。

証明書を再作成したい場合は、[ブラウザの設定(必須)](how-to-preference.md)を参照してください。

### その５：連絡先・プロジェクトについて

**連絡先：**  
X (旧Twitter): [@nomonomo_info](https://x.com/nomonomo_info)

**プロジェクトの経緯：**  
本プロジェクトは、[sdk0815さん](https://github.com/sdk0815/logbook-kai)のフォークをベースに、機能追加や改修を行ってきたものです。

**開発状況について：**  
開発者が勉強を兼ねて開発しているため、以下の点をご了承ください。

- レスポンスや修正対応が遅くなる場合があります
- Xでのお問い合わせには、あまり反応できない可能性があります
- バグ報告や機能要望は、GitHubの [Issues](https://github.com/nomonomo/logbook-kai/issues) に記載していただけると助かります（対応をお約束するものではありません）


### 概要

**航海日誌 (logbook-kai)** は、「艦隊これくしょん ～艦これ～」をより遊びやすくするための外部ツールです。

画面がコンパクトなのが特徴です。

![メイン画面](images/overview.png)

![メイン画面(ワイド)](images/overview-wide.png)

### 航海日誌 について

航海日誌 では[Jetty](http://www.eclipse.org/jetty/) で通信内容をキャプチャして内容を解析／表示します。
プロキシ設定を行うことで別のツールと連携することも可能です。

**「艦隊これくしょん ～艦これ～」サーバーに対する通信内容の改変、追加の通信等は一切行っていません。**

MIT ライセンスの下で公開する、自由ソフトウェアです。

### 主な機能

* 遠征・入渠の通知機能 : 1分前になると自動的に通知します。
* 海戦・ドロップ報告書 : 戦闘の状況、ドロップ艦娘などの情報の収集を行えます。
* 所有装備一覧 : 誰がどの装備を持っているかを簡単に確認することが出来ます。
* 所有艦娘一覧 : 艦娘の各種パラメータ(コンディション、制空値、火力値等)の閲覧を行うことが出来ます。
* お風呂に入りたい艦娘 : 修理が必要な艦娘の時間と必要資材を一覧で見ることが出来ます。


### 動作環境
![Java](https://img.shields.io/badge/-Java-007396.svg?logo=java)
![Windows](https://img.shields.io/badge/-Windows-0078D6.svg?logo=windows)
![Debian](https://img.shields.io/badge/-Debian-A81D33.svg?logo=debian)
![Redhat](https://img.shields.io/badge/-Redhat-EE0000.svg?logo=red-hat)
![macOS](https://img.shields.io/badge/-macOS-333333.svg?logo=apple)

**JREを作成する形にしたのでJavaのインストールは不要になりました**

Windows版のみ作成しております


### [ダウンロード](https://github.com/nomonomo/logbook-kai/releases)

**ご注意ください**

**初期の状態では艦娘の画像が表示出来ません。必ず**[FAQ](faq.md)**をお読みください。**

### [ブラウザの設定(必須)](how-to-preference.md)

### [FAQ](faq.md)

#### プラグイン
* [Pushbullet Plugin](https://github.com/rsky/logbook-kai-plugins)
  * 遠征・入渠の通知をiPhone/Android端末へプッシュ通知することが可能になります。

### スクリーンショット

* メイン画面

![メイン画面](images/overview.png)

* 所有装備一覧

![所有装備一覧そのいち](images/items1.png)
![所有装備一覧そのに](images/items2.png)

* 戦闘ログ

![戦闘ログそのいち](images/battlelog1.png)
![戦闘ログそのに](images/battlelog2.png)

### 開発者向け

#### [ビルド方法](how-to-build.md)

#### [プラグイン開発](how-to-develop.md)

### ライセンス

* [The MIT License (MIT)](LICENSE)

MIT ライセンスの下で公開する、自由ソフトウェアです。

### 使用ライブラリとライセンス

以下のライブラリを使用しています。

#### [JSON Processing(JSR 353)](https://jsonp.java.net/)

* COMMON DEVELOPMENT AND DISTRIBUTION LICENSE (CDDL - Version 1.1)
* GNU General Public License (GPL - Version 2, June 1991) with the Classpath Exception
* **ライセンス全文 :** [https://jsonp.java.net/license.html](https://jsonp.java.net/license.html)

#### [Jetty](http://www.eclipse.org/jetty/)

* Apache License 2.0
* Eclipse Public License 1.0
* **ライセンス全文 :** [http://www.eclipse.org/jetty/licenses.php](http://www.eclipse.org/jetty/licenses.php)

#### [commons-logging](https://commons.apache.org/proper/commons-logging/)

* Apache License 2.0
* **ライセンス全文 :** [http://www.apache.org/licenses/](http://www.apache.org/licenses/)

#### [ControlsFX](http://fxexperience.com/controlsfx/)

* The BSD 3-Clause License
* **ライセンス全文 :** [https://bitbucket.org/controlsfx/controlsfx/src/default/license.txt?fileviewer=file-view-default](https://bitbucket.org/controlsfx/controlsfx/src/default/license.txt?fileviewer=file-view-default)
