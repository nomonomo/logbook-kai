/**
 * logbook-jlinkモジュール定義
 * 
 * このモジュールはjlinkパッケージング専用のラッパーモジュールです。
 * 実際のアプリケーションコードはlogbookモジュールに含まれています。
 * 
 * 注意: このファイルはjlinkプラグインがモジュールを認識するために必要です。
 * jarファイルは作成されません（maven-jar-pluginでスキップ）。
 */
module logbook.jlink {
    requires logbook;
}

