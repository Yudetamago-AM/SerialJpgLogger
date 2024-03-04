# SerialJpgLogger
シリアルポートから連続して入力される16進文字列のJPG画像を、バイナリ形式でファイルに書き出すためのプログラムです。
クロスプラットフォームであることが特徴です。
![SerialJpgLogger](https://github.com/Yudetamago-AM/SerialJpgLogger/assets/104749511/fa334ded-16c2-49b5-833e-31842e06ee75)

## 使い方
1. 画像・受信データの保存先を選ぶ
2. シリアルポートを選んで開く
   - 今のところ115200bpsのみ対応。

## ダウンロード
[リリース](https://github.com/Yudetamago-AM/SerialJpgLogger/releases)よりダウンロードできます。
今のところ、Windows 11とLinux x64用のファイルを用意しています。JavaFXとJREが用意できればjarファイルを用いて他のプラットフォームでも動くはずです。

## 仕様
- 入力はバイナリでなく、16進ダンプ形式。具体的に、配列data（長さn）にJPG画像が入っているとして、```for (int i = 0; i < n; i++) printf("%02x", data[i]);```という形で出力される形のデータの入力を期待する。
- 入力文字列の空白類を無視する。
-	'0'-'9', 'a'-'f', 'A'-'F'（16進表記の記号）でない記号は’0’とみなす。
-	直近64文字のうち空白類を除いた文字が全て’0’ならば画像ごとの区切りとみなす。
  - つまり、画像データと画像データの間に64個以上の’0’が必要です。
- 指定されたディレクトリ内に、"log[yyyymmdd]\_[hhmmss].jpg"（バイナリ）および"log[yyyymmdd]\_[hhmmss]_raw.hex"（テキストファイル）を出力する。ただし、[yyyymmdd]および[hhmmss]は受信日時を表す。
  - 一秒間に複数枚の画像を受信することは今のところ想定していない

## 他のプラットフォーム(macOS等)で使う
### 方法1（要JDKインストール）
前提：JDK17以上をインストール済み

1. [ここ](https://gluonhq.com/products/javafx/)からJavaFXをダウンロード・解凍
   - 使いたいプラットフォーム向けでTypeが**SDK**となっているもの
4. SerialJpgLogger_vX_Jar.zip（Xはバージョン）をダウンロード・解凍
6. ```cd <SerialJpgLogger_vX_Jar.zipを解凍したディレクトリ>```
7. ```echo "java --module-path "<JavaFXを解凍したディレクトリ>/lib" --add-modules javafx.controls -jar sjl_vX.jar" > run.sh```
8. ```chmod +x run.sh```
9. ```./run.sh```で実行

### 方法2（JDKのインストール不要）

1. [ここ](https://gluonhq.com/products/javafx/)からJavaFXをダウンロード・解凍
   - 使いたいプラットフォーム向けでTypeが**jmods**となっているもの
2. [ここ](https://jdk.java.net/21/)からOpenJDK 21.0.2をダウンロード・解凍
   - 使いたいプラットフォーム向けのビルド
4. SerialJpgLogger_vX_Jar.zip（Xはバージョン）をダウンロード・解凍
5. ```cd <SerialJpgLogger_vX_Jar.zipを解凍したディレクトリ>```
6. ```<OpenJDKを解凍したディレクトリ>/jdk-21.0.2/bin/jlink --module-path "<OpenJDKを解凍したディレクトリ>/jmods" --module-path "<JavaFXを解凍したディレクトリ>/javafx-jmods-21.0.2" --add-modules java.base,java.desktop,jdk.unsupported,javafx.base,javafx.controls,javafx.graphics --output jre_min```
7. ```echo "jre_min/bin/java.exe -jar sjl_vX.jar" > run.sh```
8. ```chmod +x run.sh```
9. ```./run.sh```で実行
