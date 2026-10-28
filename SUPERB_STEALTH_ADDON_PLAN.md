# SuperbWarfare × Stealth by Karl Karlmann 連携アドオン 開発計画書

## 1. プロジェクト概要

### 1.1 目的
Minecraft 1.20.1 (Forge) 環境において、近代火器 Mod **「SuperbWarfare」** と ステルス・知覚拡張 Mod **「Stealth by Karl Karlmann」** を連携させる独立アドオン Mod を新規開発する。

### 1.2 背景と現状の課題
- **課題 1: 銃声にウォーデンや Stealth の Mob が反応しない**
  - SuperbWarfare の銃器は、発砲時にサウンドパケット（効果音）を直接クライアントに送信しているのみで、Minecraft の振動感知の要である `GameEvent` を一切発行していない。
  - 弾丸（`FastThrowableProjectile`）も独自弾道計算のため `super.baseTick()` を呼んでおり、バニラ弾丸の `PROJECTILE_SHOOT`（発射振動）もスキップされている。
- **課題 2: サプレッサーがゲームメカニクスに影響を与えない**
  - サプレッサー（消音器）を装着しても、再生される発砲音の音声ファイルが切り替わるだけで、敵の索敵や隠密判定に対するゲーム的な恩恵がない。
  - また、一般のシューターに見られる「消音の代わりに連射速度や弾速が下がる」といったトレードオフ（バランス調整）も存在しない。
- **課題 3: 単純なコンフィグ調整の破綻**
  - Stealth Mod 側の検知半径（`base_hearing_range`）を広げると、銃声だけでなく「プレイヤーの普通の足音」まで 60m〜100m 先の Mob に丸聞こえになってしまい、ステルスゲームとして崩壊する。

---

## 2. 内部仕様・調査結果の総括

### 2.1 SuperbWarfare 内部仕様
| 要素 | 実装場所 / クラス | 仕様詳細 |
| :--- | :--- | :--- |
| **発砲イベント** | `com.atsuishio.superbwarfare.api.event.ShootEvent.Post` | Forge EventBus に公開されている公式イベント。射撃者（`shooter`）、ワールド（`level`）、銃データ（`data: GunData`）、発砲座標（`shootPosition`）を完全取得可能。 |
| **サプレッサー判定** | `GunData.attachment.get(AttachmentType.BARREL)` | 戻り値が `2` の場合、バレルアタッチメントにサプレッサーが装着されている。全銃器共通。 |
| **銃声半径** | `data.get(GunProp.SOUND_RADIUS)` | 各銃器固有の銃声伝播半径（スナイパーは広く、ハンドガンは狭い）。 |
| **性能補正機構** | `GunItem.modifyProperty(PMC<GunData, DefaultGunData>)` | 各フレーム/キャッシュ計算時にステータスを動的算出。`getCustomRPM(data)` 等をフック可能。 |

### 2.2 Stealth by Karl Karlmann 内部仕様
| 要素 | 実装場所 / クラス | 仕様詳細 |
| :--- | :--- | :--- |
| **検知半径拡張** | `MixinGameEventDispatcher`, `MixinMob` | バニラの 16m 制限を `@Redirect` し、`StealthConfig.COMMON.BASE_HEARING_RANGE`（標準 25m、最大 128m）まで拡張済み。 |
| **Mob 聴覚状態** | `StealthStateProvider.STEALTH_CAPABILITY` (`IStealthState`) | 各 Mob に付与される Capability。怪しい音の座標（`setSuspiciousLocation`）や警戒度（`addAlertLevel`）を直接操作可能。 |
| **警戒度遷移** | `DistractionGoal`, `MixinMob` | 警戒度 `0.2` 以上で音源を注視（Phase 1）、`0.45` 以上で音源へ移動調査（Phase 2）。村人などの `TIMID` Mob はパニック逃走。 |
| **Escalation イベント** | `StealthConfig.COMMON.ESCALATION_EVENTS` | `entity_damage`, `explode` 等。1回の振動で警戒度が `+0.3`（通常は `+0.15`）増加し、上限が `1.0`（最大警戒・戦闘態勢）まで解放される。 |
| **距離減衰の特性** | `MixinMob (180〜188行目)` | 距離によるペナルティが最大でも `0.8` しか引かれないため、`base_hearing_range` を広げすぎると足音（Priority 5.0）まで遠距離に届いてしまう。 |

---

## 3. アドオンの要件定義とアーキテクチャ

```mermaid
flowchart TD
    subgraph SuperbWarfare["SuperbWarfare (発砲)"]
        SE["ShootEvent.Post 発火"]
    end

    subgraph Addon["新規アドオン (本Mod)"]
        Handler["Event Listener (ShootHandler)"]
        Check{"サプレッサー装着?<br/>(BARREL == 2)"}
        VanillaVib["バニラ GameEvent.PROJECTILE_SHOOT<br/>(または EXPLODE) を発火"]
        CapAlert["SOUND_RADIUS 内の Mob を走査<br/>STEALTH_CAPABILITY に直接座標＆警戒度付与"]
        Mixin["GunItem#modifyProperty への Mixin<br/>(RPM を 15%〜20% 低下)"]
    end

    subgraph Target["検知対象"]
        Warden["ウォーデン<br/>(バニラ振動システム)"]
        StealthHUD["Stealth HUD<br/>(音波メーターが激しく跳ねる)"]
        StealthMobs["周囲の Mob<br/>(調査・戦闘・逃走AIへ遷移)"]
    end

    SE --> Handler
    Handler --> Check
    Check -- "サプレッサーなし" --> VanillaVib
    Check -- "サプレッサーなし" --> CapAlert
    Check -- "サプレッサーあり" -->|"至近距離 (8m) のみ発火<br/>遠距離走査はスキップ"| VanillaVib

    VanillaVib --> Warden
    VanillaVib --> StealthHUD
    CapAlert --> StealthMobs
    Mixin -.->|"武器性能に介入"| SuperbWarfare
```

### 3.1 仕様詳細
1. **足音と銃声の完全分離**
   - `stealth-common.toml` の `base_hearing_range` は `25`（標準値）のまま維持。
   - 銃声のみ、銃ごとの `SOUND_RADIUS`（例: 50m〜80m）に基づいて Mob の Capability へ直接音源座標と警戒度を注入する。
2. **サプレッサーのトレードオフ（ゲームバランス）**
   - **消音効果**: 遠距離への直接通知をカットし、バニラ `GameEvent` も 8m 以内のみに限定（完全消音もコンフィグで選択可）。
   - **性能ペナルティ**: `GunItem` への Mixin により、サプレッサー装着時の発射レート（`GunProp.RPM`）を元の 80%〜85% に低下させる（UI/ツールチップにも自動反映）。

---

## 4. 段階的実装ロードマップ

### Phase 1: 基礎発砲検知（最小プロトタイプ・Mixin不要）
- **ゴール**: 銃を撃った瞬間にウォーデン、近くの Mob、Stealth HUD が反応することを確認する。
- **実装内容**:
  1. Forge EventBus で `ShootEvent.Post` を受信するリスナを作成。
  2. 発砲地点で `serverLevel.gameEvent(shooter, GameEvent.PROJECTILE_SHOOT, shootPos)` を呼び出す。
- **検証手順**:
  - ゲーム内で銃を発砲し、画面上の Stealth HUD の音波メーターが跳ね上がるか確認。
  - 25m 以内にいるウォーデンやゾンビが発砲者を振り向くか確認。

### Phase 2: サプレッサー消音 ＆ 遠距離伝播（Stealth Capability 連携）
- **ゴール**: サプレッサーなし時は遠くの敵（50m+）が集まり、サプレッサー装着時は敵に気づかれない状態を作る。
- **実装内容**:
  1. `data.attachment.get(AttachmentType.BARREL) == 2` による条件分岐。
  2. **サプレッサーなし**:
     - `shootPos` を中心に `SOUND_RADIUS` 内の `Mob` を取得。
     - 各 Mob の `StealthStateProvider.STEALTH_CAPABILITY` を取得し、`setSuspiciousLocation(shootPos, priority, time)` および `addAlertLevel(0.4F)` を呼ぶ。
  3. **サプレッサーあり**:
     - 遠距離通知をスキップ。至近距離（8m）のみ小さな GameEvent を発生させる。
- **検証手順**:
  - 50m 先のゾンビが、サプレッサーなし射撃で走り寄ってくるか確認。
  - サプレッサー装着射撃で、15m 離れたゾンビが反応しないか確認。

### Phase 3: 武器バランス調整（Mixin による RPM 低下）
- **ゴール**: サプレッサー装着時に武器の連射速度（RPM）が低下するペナルティを実装する。
- **実装内容**:
  1. `GunItem` に対する Mixin を作成し、`modifyProperty` または `getCustomRPM` をフック。
  2. `BARREL == 2` の場合、計算後の RPM から `15%`（または固定値）を減算。
- **検証手順**:
  - アタッチメント台でサプレッサーを付けた際、銃の性能表示（RPM）が下がっているか確認。
  - 実際の射撃時の連射速度が低下しているか確認。

### Phase 4: コンフィグ化と設定調整
- **ゴール**: サーバー管理者が検知距離や低下率をカスタマイズできるようにする。
- **実装内容**:
  - `superb_stealth-common.toml` を作成：
    - `unsuppressed_hearing_multiplier`（銃声半径の倍率）
    - `suppressed_hearing_range`（サプレッサー時の最大検知距離、デフォルト 8.0）
    - `suppressor_rpm_penalty_rate`（RPM 低下率、デフォルト 0.15 = -15%）

---

## 5. 新規プロジェクト環境構築ガイド

### 5.1 開発環境スペック
- **Minecraft**: 1.20.1
- **Minecraft Forge**: 47.3.0 以降
- **Java**: 17 (Eclipse Adoptium JDK 17)
- **マッピング**: Mojang Official (`official_1.20.1`)

### 5.2 依存関係の設定（`build.gradle` 例）
新規プロジェクトの `libs/` フォルダに対象 Mod を配置し、フラットディレクトリ参照させる構成が確実です。

```groovy
repositories {
    flatDir {
        dir 'libs'
    }
}

dependencies {
    minecraft 'net.minecraftforge:forge:1.20.1-47.3.0'

    // SuperbWarfare
    implementation fg.deobf("blank:superbwarfare-0.8.9.2-mc1.20.1-65d985a64-all")

    // Stealth by Karl Karlmann
    implementation fg.deobf("blank:stealth_1.20.1_forge_1.1.100")

    // 必要に応じて GeckoLib 等（SuperbWarfare の前提）
}
```

### 5.3 パッケージ構成案
```text
src/main/java/net/addon/superbstealth/
├── SuperbStealthMod.java        // Mod メインクラス
├── config/
│   └── SuperbStealthConfig.java // TOML コンフィグ
├── event/
│   └── GunShootEventListener.java // Phase 1 & 2: 発砲検知と振動・Capability 制御
└── mixin/
    └── GunItemMixin.java         // Phase 3: サプレッサー RPM 低下 Mixin
```
