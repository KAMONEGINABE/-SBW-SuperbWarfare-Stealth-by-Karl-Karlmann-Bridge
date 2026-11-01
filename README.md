# SuperbWarfare × Stealth Bridge

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen.svg)](https://minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.3.0+-orange.svg)](https://files.minecraftforge.net/)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

An unofficial bridge addon that connects the modern firearms of **SuperbWarfare** with the dynamic perception and acoustic AI mechanics of **Stealth by Karl Karlmann**.

[English](#english) | [日本語](#日本語)

---

## English

### 1. Overview

An unofficial bridge addon that intercepts SuperbWarfare gun firing events and routes sound through a lightweight acoustic occlusion and diffraction model, dispatching alertness and vibrations directly to mobs from Stealth Mod, the Warden, and Sculk Sensors.

### 2. Dependencies

| Dependency | Required Version | Note |
| :--- | :--- | :--- |
| **Minecraft** | `1.20.1` | |
| **Minecraft Forge** | `47.4.28` | |
| **SuperbWarfare** | `0.8.9.2` or higher | Main firearm framework |
| **Stealth by Karl Karlmann** | `1.1.100` or higher | Stealth AI & capability system |
| **Kotlin for Forge** | `4.12.0` | Bundled with SuperbWarfare |

### 3. Building

This project uses Gradle with the Java Toolchain. Dependencies are automatically resolved and deobfuscated via CurseMaven, so there is no need to manually place dependency jars in a `lib` directory.

### 4. Technical Overview

- **Acoustic Occlusion (`SoundOcclusionHelper`)**:
  - Implements a lightweight approximation model using Minecraft `VoxelShape` collision bounds and raycasts.
  - **Penetration**: Calculates distance attenuation based on actual physical obstruction thickness.
  - **Diffraction**: When line-of-sight is blocked, finds detour routes through openings like open doors or stairs via branch-and-bound search up to 3 hops (hop count is configurable).
  - Uses the route with higher sound transmission for calculation.
- **Mob AI Integration (`StealthAlertHandler`)**:
  - Bypasses the vanilla 16m acoustic limit of Warden-based listening by directly adding sound coordinates and alert values to each mob's `IStealthState`.
  - Individually increments anger for the Warden.
- **Sculk Sensors (`SculkVibrationHandler`)**:
  - Schedules vibrations only for sensors that clear occlusion checks.
- **Firearm Properties (`MixinGunItem`)**:
  - With a suppressor attached, cancels distant reverb and plays close-range gunfire scaled to the detection radius. Sound attenuation applies equally to other players in multiplayer.
  - Hooks `modifyProperty` to apply multipliers for spread, RPM, muzzle velocity, recoil, and weight.
- **In-Game Debugger (`DebugParticleManager`)**:
  - Run `/superbstealth debug` to render sound paths with particles: green for direct reach, yellow for doorway detours, and red for blocked paths.

### 5. Configuration

Configurable via `config/superbstealth-common.toml`:
- `[occlusion]`: Wall and floor/ceiling penetration penalties, detour hop limits, and room exit scan radius.
- `[gun]`: Suppressor acoustic radius multiplier, minimum guaranteed radius, and fallback gunshot volume.
- `[suppressor_modifier]`: Stat multipliers with a suppressor attached: spread, damage, velocity, RPM, recoil, and added weight.
- `[stealth]`: Alert level gains, memory priority, and investigation thresholds.
- `[sculk]`: Sculk sensor vibration frequency settings.

### 6. AI-Assisted Development

This mod is an addon originally built for a private modpack and shared publicly. The code was developed primarily with AI assistance.
While I understand the overall architecture and role of each class, I do not personally write every line of low-level syntax.
That said, it has been thoroughly tested in-game and runs stably in my personal environment.
Furthermore, all commented-out code has been reviewed and refactoring has been handled with care, ensuring the codebase remains clean and readable for other developers.

### 7. Issues & Contributions

- **Bug Reports**: Please open an Issue with reproduction steps, crash logs from `crash-reports/` or `logs/latest.log`, and your mod list.
- **Pull Requests**: Bug reports and fix PRs are welcome. Since this is a personal hobby project, complex fixes may take some time.
- **Feature Requests**: Ideas and suggestions are welcome. While implementation cannot be guaranteed, features that sound interesting or fit my own gameplay may be implemented when time permits.

### 8. Credits & License

- **SuperbWarfare**: Atsuishio, Roki27, Light_Quanta, and contributors
- **Stealth by Karl Karlmann**: KarlKarlmann
- License: **GNU General Public License v3.0 (GPL-3.0)**. See [LICENSE](LICENSE) for details.

---

## 日本語

### 1. プロジェクト概要とスタンス

本 Mod は、SuperbWarfare の発砲イベントを受信し、軽量な音響遮蔽・回折モデルを通じて、Stealth Mod の Mob、ウォーデン、スカルクセンサーへ直接警戒度や振動を伝達する非公式アドオンです。

### 2. 前提環境

- **Minecraft**: `1.20.1`
- **Minecraft Forge**: `47.4.28`
- **SuperbWarfare**: `0.8.9.2` 以降
- **Stealth by Karl Karlmann**: `1.1.100` 以降
- **Kotlin for Forge**: `4.12.0` - SBWに標準で内包

### 3. ビルドについて

本プロジェクトは Gradle Java Toolchainを使用しています。
ビルド時には前提 Modは CurseMaven を利用して自動的にダウンロードおよび難読化解除されるため、libフォルダ内へ前提modを配置する必要はありません。

### 4. コードの概要（開発者向け）

- **物理音響遮蔽モデル (`SoundOcclusionHelper`)**:
  - Minecraft の `VoxelShape` 当たり判定とレイキャストを活用した軽量近似モデルを実装。
  - **貫通減衰**: 障害物の実貫通厚みに応じた距離減衰を計算。
  - **回折迂回**: 直線が遮蔽された場合、開いたドアや階段などの開口部を経由するルートを最大3回の屈折まで探索。（屈折回数はコンフィグで変更可能）
  - より伝達量の大きい方を計算に利用する。
- **Mob AI 連携 (`StealthAlertHandler`)**:
  - ウォーデンの音認識を用いた聴覚システムは16mまでの距離制限が存在していたので、各 Mob の `IStealthState` に音源座標と警戒度を直接加算して制限を回避。
  - ウォーデンに対しては個別に怒り値加算を実装。
- **スカルクセンサー (`SculkVibrationHandler`)**:
  - 遮蔽をクリアしたセンサーにのみ振動をスケジュール。
- **銃器プロパティ介入 (`MixinGunItem`)**:
  - サプレッサー装着時、遠距離リバーブ音をキャンセルし、至近距離音のみを検知距離に合わせて再生。マルチプレイ時の他プレイヤーの耳にも音の減衰が連動する。
  - `modifyProperty` をフックし、集弾率・RPM・初速・反動・重量などの倍率補正を適用。
- **ゲーム内デバッグ表示 (`DebugParticleManager`)**:
  - `/superbstealth debug` コマンドで音波伝播（緑: 直進到達、黄: ドア迂回、赤: 遮断）をパーティクル描画。

### 5. 設定

設定は `config/superbstealth-common.toml` から変更可能です：
- `[occlusion]`: 壁・床天井の貫通減衰値、迂回ホップ上限、部屋出口探索範囲。
- `[gun]`: サプレッサー時の音響半径倍率、最低保証半径、フォールバック音量。
- `[suppressor_modifier]`: サプレッサー装着時の各種性能倍率（集弾率、威力、初速、RPM、反動、追加重量）。
- `[stealth]`: 警戒度増加量、記憶優先度、調査移行しきい値。
- `[sculk]`: スカルクセンサーの振動周波数設定。

### 6. AI利用について

本modは非公開の個人用 Modpack 向けに制作したアドオンを公開しているもので、コードは主に AI の支援を借りて作成しています。
全体の設計や各クラスの役割は把握していますが、細かい構文を全行自力で把握しているわけではありません。
とはいえ、ゲーム内でのテストは重ねており、個人環境では安定して動作しています。
またコメントアウトには全て目を通し、リファクタリングにも一定の注意を払っていますので、開発者向けにもある程度読みやすいコードになっているはずです。

### 7. Issue・コントリビューション方針

- **バグ報告**: 再現手順、クラッシュログ（`crash-reports/` または `logs/latest.log`）、導入 Mod 一覧を添えて Issue を作成してください。
- **プルリクエスト**: バグ報告や修正 PR は歓迎します。個人開発のため、複雑な修正対応にはお時間をいただく場合があります。
- **機能提案**: アイデアや新機能の提案も歓迎します。実装する約束はできませんが、面白そうなアイデアや作りたくなった/遊んでみたくなった機能は、時間がとれた際に作るかもしれません。

### 8. クレジット & ライセンス

- **SuperbWarfare**: Atsuishio 氏, Roki27 氏, Light_Quanta 氏 ほか
- **Stealth by Karl Karlmann**: KarlKarlmann 氏
- ライセンス: **GNU General Public License v3.0 (GPL-3.0)**（詳細は [LICENSE](LICENSE) を参照）
