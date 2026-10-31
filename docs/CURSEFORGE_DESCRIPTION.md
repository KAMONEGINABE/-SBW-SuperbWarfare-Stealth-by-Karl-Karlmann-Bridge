# SuperbWarfare × Stealth Bridge (CurseForge / Modrinth Description)

[English](#english) | [日本語](#日本語)

---

## English

> **Note on Development & AI Assistance**:  
> Code is mostly written with AI assistance. I understand the overall design and what each class does, but I don't necessarily know every fine detail of the code.  
> That said, it has been thoroughly tested both in-game and internally, and works very smoothly on my end.  
> I've also put care into refactoring and code comments, so if you're a fellow developer interested in this mod, definitely check out the source code!  
> If you encounter issues, feel free to report them (though complex fixes might take some time).

### Overview

This addon is designed for SuperbWarfare firearms, with indoor combat—such as clearing abandoned structures in **The Lost Cities**—as a primary target environment.

- **Outdoors**: Firing an unsuppressed rifle draws enemies from far away, bringing the tension of triggering a mob horde.
- **Indoors**: Equipping a suppressor on an SMG and clearing rooms methodically allows you to clear out an entire skyscraper while staying undetected for most of the run.

In a standard SuperbWarfare setup, gunfire is merely played as sound effects without reaching the Warden, and suppressors simply swap the audio file without changing the actual audible distance. This addon fills those gaps to deliver a true stealth shooter experience.

### Features

- **Realistic Gunshot Reach**: Rifles carry far across open areas, while handguns stay within a tighter perimeter, alerting enemies based on each gun's volume.
- **Walls & Floors Muffle Sound**: Solid walls, floors, and ceilings weaken sound passing through them.
- **Sound Travels Around Open Doors**:
  - Even if direct line-of-sight is blocked, sound travels around corners through open doors, stairwells, and hallways.
  - *Note: This uses a lightweight heuristic based on Minecraft block collisions rather than heavy acoustic raytracing, keeping gameplay smooth.*
- **Functional Suppressors**:
  - Dramatically cuts down detection range (~75% reduction by default).
  - On top of vanilla SBW suppressor attributes, custom stat trade-offs can be freely configured (slightly increases bullet spread by default).
- **Synchronized Audio for Other Players in Multiplayer**:
  - In addition to mob AI detection, the gunshot audio heard by other players' ears in multiplayer is scaled down to match the detection radius.
- **Warden & Sculk Integration**: Gunfire agitates the Warden and triggers nearby sculk sensors.
- **In-Game Sound Visualizer**: Type `/superbstealth debug` in chat to see colorful particle paths showing how sounds travel (green = reached direct, yellow = detoured through doors, red = muffled by walls).

### Requirements

- Minecraft `1.20.1` (Forge `47.3.0`+)
- **SuperbWarfare** (`0.8.9.2`+)
- **Stealth by Karl Karlmann** (`1.1.100`+)
- **Kotlin for Forge** (`4.12.0`)

### Configuration

Everything can be tweaked in `config/superbstealth-common.toml` (wall muffling penalties, suppressor range, weapon stat multipliers, etc.).

### Credits & License

- Huge thanks to the authors of **SuperbWarfare** and **Stealth by Karl Karlmann** for their awesome mods!
- Licensed under **GPL-3.0**.

---

## 日本語

### 紹介

本 Mod は、**「The Lost Cities」** などの廃墟ビル群における屋内戦闘をひとつの想定環境として開発された、SBWの銃器向けアドオンです。

- **野外**: ライフルを撃てば遠くの敵まで引き寄せ、時にはホードを誘発したようなスリルを味わえます。
- **屋内**: サプレッサー付きサブマシンガンを手に慎重に立ち回れば、大部分の時間を敵に気づかれないままビル一棟丸ごと制圧・クリアリングできます。

標準的なSBW環境では、銃声が効果音として再生されるだけでウォーデンに聞こえておらず、サプレッサーは音源ファイルが変更されるだけで聞こえる距離に変化がなかったりしました。本アドオンはこれらを補完して本格的なステルスシューター体験を提供します。

### 機能

- **銃声がしっかり届く**: ライフルは遠くまで響き、ハンドガンは狭い範囲に収まるなど、銃ごとの音量に応じて敵が気づきます。
- **壁や床で音が弱まる**: 分厚い壁や天井・床を挟むと音が遮られて届きにくくなります。
- **ドアが開いていれば音が回り込む**:
  - 直線が遮られていても、開いているドアや階段、通路を経由して音が回り込みます。
  - ※重たい音響レイトレーシング演算ではなく、Minecraft のブロック判定を活用した軽量な簡易モデルで処理しているため、ゲームの軽快さを損ないません。
- **サプレッサーがちゃんと機能する**:
  - 検知範囲が大幅に狭まり、遠くの敵に気づかれなくなります（デフォルトで約 75% カット）。
  - サプレッサ装着時のペナルティについて、SBW本体で設定されているものに加え、自由なトレードオフをコンフィグから設定可能です。（デフォルトでは少しだけ集弾性が悪化します）
- **マルチプレイでの他プレイヤーの聴覚にも連動**:
  - Mob の AI 判定だけでなく、マルチプレイ時に他のプレイヤーの耳に届く発砲音が検知範囲に合わせて小さくなります。
- **ウォーデンやスカルクも反応**: 銃声を聞いたウォーデンが怒ったり、スカルクセンサーが反応したりします。
- **音の届き方をゲーム内で可視化**: チャットで `/superbstealth debug` と打つと、音波の軌跡がパーティクルで表示されます（直進到達は緑、ドアの回り込みは黄、壁で消音されたルートは赤）。

> **開発とAI利用について**:  
> この Mod のコードは主にAIの支援を借りて作成しています。私自身は全体の設計や各クラスの役割は理解していますが、コードの細かい詳細まで全て把握しているわけではありません。
> とはいえ、ゲーム内外でしっかりテストを重ねており、私の環境ではとても快適に動いています。
> リファクタリングやコメントアウトにもある程度気を遣っているので、このmodに興味がある開発者は是非ソースコードもチェックしてみて下さい。
> もし不具合を見つけた場合はお気軽に Issue 等で教えてください（複雑な修正には少しお時間をいただくかもしれません）。

### 前提 Mod

- Minecraft `1.20.1` (Forge `47.3.0` 以降)
- **SuperbWarfare** (`0.8.9.2` 以降)
- **Stealth by Karl Karlmann** (`1.1.100` 以降)
- **Kotlin for Forge** (`4.12.0`)

### 設定（コンフィグ）

`config/superbstealth-common.toml` から、壁の減衰率やサプレッサーの消音倍率、銃の性能ペナルティなどを細かく調整できます。

### クレジット & ライセンス

- 素晴らしい元 Mod を作ってくださった **SuperbWarfare** および **Stealth by Karl Karlmann** の作者様に心より感謝いたします！
- ライセンス: **GPL-3.0**
