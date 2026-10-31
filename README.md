# SuperbWarfare × Stealth Bridge

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen.svg)](https://minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.3.0+-orange.svg)](https://files.minecraftforge.net/)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

An unofficial bridge addon that connects the modern firearms of **SuperbWarfare** with the dynamic perception and acoustic AI mechanics of **Stealth by Karl Karlmann**.

[English](#english) | [日本語](#日本語)

---

## English

### 1. Overview & Development Stance

This mod hooks into SuperbWarfare gun firing events, routes sound through a lightweight acoustic occlusion model (wall penetration & doorway diffraction), and dispatches alertness/vibrations directly to Stealth Mod's mobs, the Warden, and Sculk Sensors.

> **Development Note**:  
> This project is a personal hobby addon originally built for a private modpack and shared as-is. The codebase was developed with AI assistance. While I understand the overall design and architecture, fine-grained syntax and deep internals are beyond my casual expertise. That said, it has been thoroughly tested in-game and works reliably on my end.

### 2. Dependencies

| Dependency | Required Version | Note |
| :--- | :--- | :--- |
| **Minecraft** | `1.20.1` | |
| **Minecraft Forge** | `47.3.0` or higher | Tested on `47.4.28` |
| **SuperbWarfare** | `0.8.9.2` or higher | Main firearm framework |
| **Stealth by Karl Karlmann** | `1.1.100` or higher | Stealth AI & capability system |
| **Kotlin for Forge** | `4.12.0` | Runtime dependency |

### 3. Building from Source

This project uses Gradle with the Java Toolchain (configured for Java 17). Dependencies (SuperbWarfare and Stealth) are automatically resolved and deobfuscated via CurseMaven.

1. **Clone the repository**:
   ```bash
   git clone https://github.com/<owner>/superbstealth.git
   cd superbstealth
   ```
2. **Build the mod**:
   - Windows:
     ```cmd
     .\gradlew.bat build
     ```
   - Linux / macOS:
     ```bash
     ./gradlew build
     ```
3. The compiled jar will be generated in `build/libs/superbstealth-<version>.jar`.

### 4. Architecture & Technical Overview

- **Acoustic Occlusion (`SoundOcclusionHelper`)**:
  - Does **not** use heavy acoustic raytracing. Instead, it utilizes Minecraft's `VoxelShape` block colliders and raycasts.
  - **Penetration**: Traces straight-line rays through blocks, deducting distance penalties based on physical wall/floor thickness.
  - **Diffraction**: If line-of-sight is blocked, a branch-and-bound search finds detour routes through openings (open doors, stairs, air) up to 3 hops.
- **Mob AI Integration (`StealthAlertHandler`)**:
  - Injects sound location and alert values directly into each mob's `IStealthState` capability.
  - Directly increments the Warden's anger towards the shooter and forces vibration animations.
- **Sculk Sensors (`SculkVibrationHandler`)**:
  - Scans nearby chunks and schedules vibrations only for sensors reached by unoccluded sound waves, using distinct frequencies for unsuppressed vs. suppressed gunfire.
- **Suppressor Gun Handling (`MixinGunItem`)**:
  - Cancels long-range reverb sounds on suppressed shots, dynamically scaling close-range shot volume to match the in-game detection radius (both for mob AI and other players in multiplayer).
  - Intercepts `modifyProperty` to apply configurable stat multipliers (RPM, spread, recoil, velocity, weight).
- **In-Game Debugger (`DebugParticleManager`)**:
  - Run `/superbstealth debug` to render sound paths as colored particles (green: direct, yellow: detoured via doors, red: muffled/blocked).

### 5. Configuration

Settings are located in `config/superbstealth-common.toml`:
- `[occlusion]`: Wall and floor/ceiling penetration penalties, diffraction hop limits, and exit scan radius.
- `[gun]`: Suppressor radius multipliers, minimum audible radius, and fallback gunshot volume.
- `[suppressor_modifier]`: Stat multipliers applied when a suppressor is attached (spread, damage, velocity, RPM, recoil, weight).
- `[stealth]`: Alert level gains, memory priority, and investigation thresholds.
- `[sculk]`: Vibration frequencies and comparator outputs for sculk sensors.

### 6. Contributing & Issues

- **Bug Reports**: Please submit an Issue detailing your reproduction steps, a crash log (`crash-reports/` or `logs/latest.log`), and a mod list.
- **Feature Requests**: Ideas and suggestions are also welcome. Implementation is not guaranteed, but if an idea sounds fun and fits my own playthrough, I might build it.
- **Pull Requests**: Contributions and improvements are welcome! Please note that as a casual personal project, complex fixes may take time.

### 7. Credits & License

- **SuperbWarfare** by Atsuishio, Roki27, Light_Quanta, and contributors.
- **Stealth by Karl Karlmann** by KarlKarlmann.
- Licensed under the **GNU General Public License v3.0 (GPL-3.0)**. See [LICENSE](LICENSE) for details.

---

## 日本語

### 1. プロジェクト概要とスタンス

本 Mod は、SuperbWarfare の発砲イベントを受信し、軽量な音響遮蔽・回折モデル（壁貫通減衰 ＋ ドア迂回探索）を通じて、Stealth Mod の Mob、ウォーデン、スカルクセンサーへ直接警戒度や振動を伝達する非公式アドオンです。

> **開発スタンスについて**:  
> 個人用 Modpack 向けに制作した趣味のアドオンを現状有姿でお裾分け公開しているものです。コードは主に AI の支援を借りて作成しています。全体の設計や各クラスの役割は把握していますが、細かい構文の深部まで全て自力で把握しているわけではありません。ゲーム内でのテストを重ねており、個人環境では安定して動作しています。

### 2. 前提条件（Dependencies）

- **Minecraft**: `1.20.1`
- **Minecraft Forge**: `47.3.0` 以降（動作確認: `47.4.28`）
- **SuperbWarfare**: `0.8.9.2` 以降
- **Stealth by Karl Karlmann**: `1.1.100` 以降
- **Kotlin for Forge**: `4.12.0`（実行時前提 Mod）

### 3. ソースコードからのビルド手順

本プロジェクトは Gradle Java Toolchain（Java 17）を使用しています。前提 Mod（SuperbWarfare および Stealth）は CurseMaven を通じて自動的にダウンロードおよび難読化解除（deobf）されます。

1. **リポジトリのクローン**:
   ```bash
   git clone https://github.com/<owner>/superbstealth.git
   cd superbstealth
   ```
2. **ビルド実行**:
   - Windows:
     ```cmd
     .\gradlew.bat build
     ```
   - Linux / macOS:
     ```bash
     ./gradlew build
     ```
3. ビルドされた Jar は `build/libs/superbstealth-<version>.jar` に生成されます。

### 4. 技術概要・アーキテクチャ

- **物理音響遮蔽モデル (`SoundOcclusionHelper`)**:
  - 重たい音響レイトレーシングではなく、Minecraft の `VoxelShape` 当たり判定とレイキャストを活用した軽量近似モデルです。
  - **貫通減衰**: 障害物の実貫通厚みに応じた距離減衰を計算（壁/床天井で個別ペナルティ）。
  - **回折迂回**: 直線が遮蔽された場合、開いたドアや階段などの開口部を経由するルートを最大3ホップまで分枝限定法で探索。
- **Mob AI 連携 (`StealthAlertHandler`)**:
  - 各 Mob の `IStealthState` (Capability) に音源座標と警戒度を直接注入。
  - ウォーデンに対する怒り値加算（Anger）および振動感知アニメーションの強制スケジュール。
- **スカルクセンサー (`SculkVibrationHandler`)**:
  - 遮蔽をクリアしたセンサーにのみ振動をスケジュール（通常射撃と消音射撃で異なる周波数を出力）。
- **銃器プロパティ介入 (`MixinGunItem`)**:
  - サプレッサー装着時、遠距離リバーブ音をキャンセルし、至近距離音のみを検知距離に合わせて再生（マルチプレイ時の他プレイヤーの耳にも連動）。
  - `modifyProperty` をフックし、集弾率・RPM・初速・反動・重量などの倍率補正を適用。
- **ゲーム内デバッグ表示 (`DebugParticleManager`)**:
  - `/superbstealth debug` コマンドで音波伝播（緑: 直進到達、黄: ドア迂回、赤: 遮断）をパーティクル描画。

### 5. 設定（Configuration）

設定は `config/superbstealth-common.toml` から変更可能です：
- `[occlusion]`: 壁・床天井の貫通減衰値、迂回ホップ上限、部屋出口探索範囲。
- `[gun]`: サプレッサー時の音響半径倍率、最低保証半径、フォールバック音量。
- `[suppressor_modifier]`: サプレッサー装着時の各種性能倍率（集弾率、威力、初速、RPM、反動、追加重量）。
- `[stealth]`: 警戒度増加量、記憶優先度、調査移行しきい値。
- `[sculk]`: スカルクセンサーの振動周波数設定。

### 6. Issue・コントリビューション方針

- **バグ報告**: 再現手順、クラッシュログ（`crash-reports/` または `logs/latest.log`）、導入 Mod 一覧を添えて Issue を作成してください。
- **機能提案（Feature Requests）**: アイデアや新機能の提案も歓迎します。必ず実装するお約束はできませんが、面白そうなアイデアや作りたくなった/遊んでみたくなった機能は、時間がとれた際に作るかもしれません。
- **プルリクエスト (PR)**: 改善提案や修正 PR は歓迎します。個人開発のため、複雑な修正対応にはお時間をいただく場合があります。

### 7. クレジット & ライセンス

- **SuperbWarfare**: Atsuishio 氏, Roki27 氏, Light_Quanta 氏 ほか
- **Stealth by Karl Karlmann**: KarlKarlmann 氏
- ライセンス: **GNU General Public License v3.0 (GPL-3.0)**（詳細は [LICENSE](LICENSE) を参照）
