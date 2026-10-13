# 入門說明：這個專案在做什麼

用正式的專有名詞，搭配白話說明每個東西的作用。
想看更深的技術細節再看 [專案導覽](WALKTHROUGH.md)。

**目錄**

1. [三句話講完](#1-三句話講完)
2. [專有名詞與元件](#2-專有名詞與元件)
3. [C:\actions-runner 是什麼](#3-cactions-runner-是什麼)
4. [一次性設定：我們做了哪些事](#4-一次性設定我們做了哪些事)
5. [push 一次之後，發生了什麼](#5-push-一次之後發生了什麼)
6. [怎麼發現問題、怎麼退版](#6-怎麼發現問題怎麼退版)
7. [Demo 怎麼做](#7-demo-怎麼做)
8. [面試怎麼講](#8-面試怎麼講)
9. [面試後清理](#9-面試後清理)

---

## 1. 三句話講完

1. **CI**：push 程式碼後，GitHub Actions 自動執行**建置、單元測試、建立 container image、弱點掃描**。
2. **CD**：通過後自動**部署到 dev 環境**；**prod 環境需要人工核准**（approval gate）才會部署。
3. **Rollback**：新版本沒通過健康檢查，pipeline 會**自動回滾到上一版**，滾動更新過程中服務不中斷。

---

## 2. 專有名詞與元件

### CI/CD 概念

**CI（Continuous Integration，持續整合）**
每次程式碼變更都自動建置和測試，盡早發現問題。
→ 本專案：測試、打包、掃描、推送 image。

**CD（Continuous Delivery / Deployment，持續交付／部署）**
把通過檢查的版本自動部署到環境上。
→ 本專案：部署到 dev，核准後部署到 prod。

**Pipeline**
從程式碼到上線的一整串自動化步驟。

**Environment（環境）**
同一套程式部署到不同地方：
- **dev**：開發／測試環境，先驗證
- **prod**：正式環境，給使用者用

### GitHub Actions

**GitHub Actions**
GitHub 內建的 CI/CD 平台，依照 repo 裡的設定檔自動執行工作。

**Workflow**
一份 YAML 設定檔，定義什麼時候觸發、要做哪些事。放在 `.github/workflows/`，GitHub 會自動讀取。
→ 本專案：`ci-cd.yml`（主流程）、`deploy.yml`（部署流程）。

**Trigger（觸發條件）**
什麼事件會啟動 workflow。
→ 本專案：push 到 main、開 Pull Request。

**Job**
workflow 裡的一個工作單位，在一台機器上執行。Actions 頁面上的每個方塊就是一個 job。
→ 本專案：Build & Test、Build Scan & Push、Deploy to dev、Deploy to prod。

**Step**
job 裡的單一步驟，例如執行一個指令。

**`needs`（相依性）**
指定 job 的先後順序：前一個成功，後一個才會執行。

**Runner**
實際執行 job 的機器。分成兩種：
- **GitHub-hosted runner**：GitHub 提供的雲端機器，每次用完就銷毀
- **Self-hosted runner**：裝在自己機器上的 runner

→ 本專案：CI 用 GitHub-hosted，部署用 self-hosted。

**Artifact**
job 產生、要傳給後面 job 使用的檔案。
→ 本專案：渲染好的 K8s manifest。

**Secret**
加密儲存的敏感資料（密碼、金鑰），執行時才注入，log 裡會被遮蔽。
→ 本專案：`API_KEY`。

**Required reviewers（核准關卡）**
environment 的保護規則，指定的人核准後才能部署。
→ 本專案：prod 需要你核准。

### 容器（Container）

**Container image**
把應用程式、執行環境（例如 JRE）和相依套件打包成一個不可變的檔案。在任何裝了 container runtime 的地方，執行結果都一樣。

**Container**
image 執行起來的實體（instance）。

**Docker**
建立和執行 container 的工具。

**Dockerfile**
描述如何建立 image 的設定檔。
→ 本專案：用 multi-stage build，先用 JDK 編譯，最後的 image 只放 JRE 和 jar。

**Container registry**
存放和發布 image 的服務，類似套件庫。
→ 本專案：**GHCR**（GitHub Container Registry），網址 `ghcr.io/ww013269/rack-monitor`。

**Image tag**
image 的版本標籤。
→ 本專案：用 **commit SHA** 當 tag，每個 image 都能對應回一個 commit。

### 安全掃描

**CVE（Common Vulnerabilities and Exposures）**
公開漏洞的統一編號，例如 Log4Shell 是 `CVE-2021-44228`。

**Trivy**
開源的弱點掃描工具（Aqua Security 開發）。比對 image 裡的作業系統套件和應用程式套件版本，跟 CVE 資料庫對照。
→ 本專案：發現 **CRITICAL** 等級就讓 pipeline 失敗，image 不會被推送。

**Security gate（安全關卡）**
pipeline 中沒通過就不能往下走的檢查點。

### Kubernetes

**Kubernetes（K8s）**
container 編排（orchestration）平台：負責排程、維持副本數、自動重啟、滾動更新。

**Cluster / Node**
cluster 是一整個 K8s 環境，由一或多台 node（機器）組成。
→ 本專案：Docker Desktop 內建的單節點 cluster。

**kubectl**
操作 K8s 的命令列工具。

**Context**
kubectl 要連到哪個 cluster 的設定。
→ 本專案：每個指令都明確指定 `--context docker-desktop`，避免部署到錯的 cluster。

**Manifest**
描述 K8s 資源的 YAML 檔。採用**宣告式**：只描述「期望狀態」（例如要跑 2 個副本），K8s 自己負責達成並持續維持。

**Namespace**
cluster 內的邏輯隔離空間。
→ 本專案：`rack-dev`、`rack-prod`。

**Pod**
K8s 最小的部署單位，裡面跑一或多個 container。

**Deployment**
管理一組 Pod 的資源：要幾個副本（replicas）、用哪個 image、怎麼更新。

**Service**
給一組 Pod 一個固定的名稱和入口，只把流量導給健康的 Pod。

**ConfigMap / Secret**
分別存一般設定和敏感資料，以環境變數注入 container。設定和 image 分開，同一個 image 可以用在不同環境。

**Probe（健康檢查）**
- **Readiness probe**：能不能接流量？失敗 → 不導流量
- **Liveness probe**：還活著嗎？失敗 → 重啟 container

**Rolling update（滾動更新）**
逐步用新版 Pod 替換舊版 Pod，過程中服務不中斷。

**Rollback**
退回上一個版本（`kubectl rollout undo`）。

**Kustomize**
用 **base**（共用設定）加 **overlay**（各環境差異）管理多環境的 manifest。

---

## 3. C:\actions-runner 是什麼

### 一句話

**這是 GitHub Actions 的 self-hosted runner，裝在你的電腦上，負責執行部署 job。**

### 為什麼需要它

部署要對 K8s cluster 下 `kubectl` 指令，但 cluster 在你的電腦裡（內網），**GitHub-hosted runner 在公網，連不進來**。

self-hosted runner 解決這個問題：

```
GitHub（公網）
   ↑ runner 主動以 HTTPS 連出去詢問：
   │ 「有沒有符合我 label 的 job？」
   │ （long polling）
self-hosted runner（你的電腦）
   → 收到 job → 在本機執行 kubectl
   → 把 log 和結果回傳 GitHub
```

重點：**連線是從內往外（outbound）建立的**，不需要開放任何對外的 port，防火牆後面也能運作。公司內網的 cluster 通常也是用這種方式部署。

### 資料夾內容

| 檔案 | 用途 |
|---|---|
| `config.cmd` | 把這台機器**註冊**成 repo 的 runner。只需執行一次（已完成） |
| `run.cmd` | **啟動** runner，開始等待 job。關掉視窗 = runner 離線 |
| `.runner`、`.credentials` | 註冊後產生的設定和憑證，不能刪、不能外流 |
| `_work/` | 執行 job 時的工作目錄（下載的 artifact、暫存檔） |
| `_diag/` | runner 的診斷 log，排查問題用 |
| `bin/`、`externals/` | runner 程式本體 |

### 安裝過程

1. 從 GitHub 官方 release 下載 runner（v2.338.0），解壓到 `C:\actions-runner`
2. 透過 GitHub API 取得一次性的 **registration token**
3. 執行 `config.cmd`：
   - 名稱：`jimmy-local`
   - 自訂 label：`local-k8s`
   - 系統自動加上：`self-hosted`、`Windows`、`X64`
4. 執行 `run.cmd` → 顯示 `Listening for Jobs` = runner 上線

沒有裝成 Windows service，因為 service 帳號讀不到你帳號底下的 `kubectl` 設定（kubeconfig）。

### Label 的作用

`deploy.yml` 裡：

```yaml
runs-on: [self-hosted, Windows, local-k8s]
```

GitHub 只會把這個 job 派給**同時具備這三個 label** 的 runner。這樣部署 job 一定在能連到 cluster 的那台機器上執行。

### 安全考量

public repo 加 self-hosted runner 有風險：別人發 Pull Request 時，可能讓惡意程式碼在你的 runner 上執行。
→ 已設定**外部貢獻者的 PR 需要核准**才會執行 workflow。

### 要記得的

- 部署時 `run.cmd` 必須在執行中
- runner 離線時，部署 job 會停在 **Queued** 排隊，不會失敗；runner 上線後自動接手

---

## 4. 一次性設定：我們做了哪些事

以下只需設定一次，之後每次 push 都會自動執行。

### ① 本機建置與測試

- `mvn verify`：編譯 + 執行單元／整合測試，8 個測試全部通過
- 啟動應用程式，實際呼叫 API 驗證功能和 health endpoint

**目的：** 先確認應用程式本身正確，之後 pipeline 出錯時，才能確定是流程設定的問題。

### ② 本機驗證 container image

- `docker build` 建立 image
- `docker run` 啟動 container，呼叫 API 驗證
- `docker exec ... id` 確認 container 以**非 root 使用者**（UID 10001）執行

**目的：** 在本機先找出 Dockerfile 的問題，回饋速度比推上 CI 再等結果快得多。

### ③ 建立 GitHub repository

用 GitHub CLI：`gh repo create` 建立 `ww013269/rack-monitor`。

### ④ 設定 Environment、Secret 與安全規則

這些屬於**權限與敏感資料**，不能寫進 repo 的檔案裡，要在 GitHub 上設定：

| 設定 | 目的 |
|---|---|
| 建立 `dev`、`prod` 兩個 environment | 部署目標分開管理，各自有獨立的規則和 secret |
| `prod` 設定 required reviewers | 部署到 prod 前必須人工核准（approval gate） |
| 兩個 environment 各一把 `API_KEY` secret | 敏感資料不進版控；dev 和 prod 隔離，一邊外洩不影響另一邊 |
| 外部貢獻者的 PR 需核准才執行 workflow | 防止有人透過 PR 在 self-hosted runner 上執行惡意程式碼 |

**為什麼要在第一次 push 前完成：** workflow 第一次引用 `prod` environment 時，若它不存在，GitHub 會自動建立一個**沒有保護規則**的 prod。

### ⑤ Push 並執行第一次 pipeline

`git push` 後，GitHub 偵測到 `.github/workflows/` 下的 workflow 檔，依照 trigger 條件自動執行。workflow 檔本身就是設定，不需要在網頁上另外啟用。

第一次執行遇到兩個問題：

1. **Maven Central 回傳 HTTP 500**：外部服務暫時故障，re-run 後恢復
2. **Trivy 擋下 image**：Spring Boot 4.1.1 內建的 Tomcat 11.0.24 有 3 個 CRITICAL CVE
   → 在 `pom.xml` 覆寫 `tomcat.version` 為 11.0.27 → 掃描通過

第 2 點是 security gate 實際發揮作用的案例：**用升級修正，沒有用 ignore 清單跳過。**

### ⑥ 將 GHCR package 設為 public

GHCR 上的 image 預設是 private。設為 public 後，K8s 拉取 image 時不需要設定 **imagePullSecret**（registry 認證）。
（package 的可見度只能在 GitHub 網頁上修改）

### ⑦ 啟用 Kubernetes

Docker Desktop → Settings → Kubernetes → Enable Kubernetes。
會建立一個單節點的本機 cluster，kubectl context 名稱是 `docker-desktop`。

### ⑧ 安裝 self-hosted runner

見第 3 節。

---

## 5. push 一次之後，發生了什麼

GitHub 的 **Actions** 頁面會顯示 4 個 job，依照 `needs` 的相依關係依序執行：

```
[Build & Test]
  → [Build, Scan & Push Image]
  → [Deploy to dev]
  → [Deploy to prod]
```

### Job 1：Build & Test（GitHub-hosted runner）

1. 配置一台全新的 Ubuntu runner
2. `actions/checkout`：取得程式碼
3. `actions/setup-java`：安裝 JDK 17，並快取 Maven 相依套件
4. `mvn -B verify`：編譯 + 執行測試
5. 上傳測試報告（失敗時也會上傳，方便排查）

測試失敗 → job 失敗，後續 job 都不會執行。

### Job 2：Build, Scan & Push Image（GitHub-hosted runner）

1. **Build**：`docker build`，tag 為 `ghcr.io/ww013269/rack-monitor:<commit SHA>`
2. **Scan**：以 container 方式執行 Trivy（固定版本 0.69.3），掃描 image 中的 OS 套件和 Java 相依套件。發現 CRITICAL 且已有修正版的 CVE → exit code 1 → job 失敗
3. **Push**：掃描通過後才登入 GHCR，`docker push`
4. **Render manifests**：用 Kustomize 產生 dev、prod 的 manifest，把 image 欄位替換成剛推送的 image，存成 **artifact** 給部署 job 使用

**順序是先掃描再推送**：沒通過的 image 不會進 registry，也就不可能被部署。

### Job 3：Deploy to dev（self-hosted runner）

`deploy.yml` 是 **reusable workflow**，dev 和 prod 共用同一份部署步驟：

1. 下載 Job 2 產生的 manifest artifact
2. 建立 namespace（已存在則略過）
3. 從 GitHub environment secret 建立 K8s Secret
4. `kubectl apply`：套用 manifest，觸發 rolling update
5. **K8s 自行從 GHCR 拉取 image**（runner 本身不處理 image）
6. `kubectl rollout status --timeout=150s`：等待新版 Pod 全部 ready
7. **Smoke test**：在 cluster 內啟動臨時的 curl Pod，呼叫 `/api/info` 確認服務可用
8. 若上述任一步驟失敗 → 執行 rollback（見第 6 節）

### Job 4：Deploy to prod（self-hosted runner）

1. 因為 `prod` environment 設有 required reviewers，job 停在 **Waiting for review**
2. 核准後執行與 Job 3 相同的步驟，部署到 `rack-prod`

Job 3 失敗 → Job 4 被 skipped，問題版本不會進到 prod。

---

## 6. 怎麼發現問題、怎麼退版

### 偵測：Readiness probe

應用程式透過 Spring Boot Actuator 提供 `/actuator/health/readiness`，正常時回傳 `{"status":"UP"}`。

Deployment 設定 K8s **每 5 秒**呼叫一次：

- 回傳 UP → Pod 標記為 **Ready**，Service 開始導流量給它
- 失敗或無回應 → Pod 標記為 **Not Ready**，不接收流量

另有 **liveness probe**：連續失敗時 K8s 會重啟 container，處理程式卡死的狀況。

### 零停機：Rolling update 策略

```yaml
strategy:
  rollingUpdate:
    maxUnavailable: 0
    maxSurge: 1
```

- `maxSurge: 1`：更新時可以多開 1 個 Pod
- `maxUnavailable: 0`：可用的 Pod 數量不能低於設定值

更新流程：

```
1. 建立 1 個新版 Pod（舊版不動）
2. 等待新版 Pod 通過 readiness probe
3a. Ready → 終止 1 個舊版 Pod，重複直到全部替換 ✅
3b. 一直 Not Ready → 舊版 Pod 不會被終止，持續服務
```

所以新版本有問題時，**舊版本仍持續提供服務**。

### 自動 rollback

runner 執行 `kubectl rollout status --timeout=150s`：

- 150 秒內完成 rollout → 成功，繼續 smoke test
- 逾時 → step 失敗 → 觸發 `if: failure()` 的步驟：
  1. `kubectl rollout undo`：回滾到上一個 revision
  2. `kubectl rollout status`：確認回滾完成

dev 部署失敗 → prod job 被 skipped，問題版本不會進到正式環境。

---

## 7. Demo 怎麼做

### 先說建議

每次跑完整流程要 **5–10 分鐘**，面試時間寶貴。建議：

1. **主要用已經跑過的紀錄 + 截圖來講**（[專案導覽](WALKTHROUGH.md#實際驗證紀錄)有截圖）
2. 面試官想看現場的，再做 Demo 1

### 面試前準備（提早 30 分鐘做）

**① 打開 Docker Desktop**
等左下角的 Kubernetes 圖示變綠色。

**② 啟動 self-hosted runner**
開 PowerShell（不是 Git Bash）：

```powershell
cd C:\actions-runner
.\run.cmd
```

看到 `Listening for Jobs`，視窗保持開著。

**③ 確認 K8s 上的程式正常**
另開一個 PowerShell：

```powershell
kubectl --context docker-desktop get pods -A
```

`rack-dev` 有 1 個、`rack-prod` 有 2 個，都是 `Running`。

**④ 確認沒有卡住的部署**
打開 https://github.com/ww013269/rack-monitor/actions
最上面那次不能是黃色的「Waiting」。是的話，點進去按 Review deployments → Approve 或 Reject。

**⑤ 先開好分頁**

- repo 首頁
- Actions 頁面
- 專案導覽的截圖段落

### 怎麼看程式在 K8s 上跑

```powershell
kubectl --context docker-desktop -n rack-dev port-forward svc/rack-monitor 8081:80
```

瀏覽器打開 http://localhost:8081/api/info
看完回到視窗按 Ctrl+C 結束。

（prod 的話：`rack-dev` 改 `rack-prod`，`8081` 改 `8082`）

會看到像這樣的內容：

```json
{"app":"rack-monitor","version":"6ff6047",
 "env":"dev","alertThreshold":27.0}
```

- `version`：目前跑的是哪個 commit
- `env`：哪個環境

### Demo 1：正常部署（約 6 分鐘）

**目標：** 改一個設定 → push → 看它自動部署上去。

1. 在 VS Code 打開 `k8s/overlays/dev/kustomization.yaml`
2. 把 `RACK_THRESHOLD=27.0` 改成 `RACK_THRESHOLD=28.0`，存檔
3. 在終端機：

   ```bash
   git add .
   git commit -m "demo: raise dev alert threshold"
   git push
   ```

4. 打開 Actions 頁面，邊等邊講：
   - Build & Test：「CI 第一階段：建置並執行測試」
   - Build, Scan & Push：「建立 container image，用 Trivy 做弱點掃描，通過才推送到 GHCR」
   - Deploy to dev：「self-hosted runner 用 kubectl 部署到 K8s，等 rolling update 完成」
5. dev 完成後，用上面的 port-forward 打開 `/api/info`
   → `alertThreshold` 變成 **28.0**，`version` 變成新的
6. prod 停在 Waiting for review：「正式環境要人工核准」→ 按 Approve
7. prod 完成後看 prod 的 `/api/info`
   → **version 和 dev 一樣**，但 `alertThreshold` 還是 **27.0**
   → 「同一個 image，不同環境有不同設定」

### Demo 2：漏洞被擋（約 4 分鐘）

**目標：** 加入有漏洞的套件 → Trivy 擋下。

1. 打開 `pom.xml`，在 `<dependencies>` 下一行加入：

   ```xml
   <dependency>
       <groupId>org.apache.logging.log4j</groupId>
       <artifactId>log4j-core</artifactId>
       <version>2.14.1</version>
   </dependency>
   ```

2. push：

   ```bash
   git add .
   git commit -m "demo: add vulnerable log4j"
   git push
   ```

3. Actions 頁面：Build & Test ✅ → Build, Scan & Push ❌
4. 點進去，展開 **Scan image with Trivy** → 看到 `CVE-2021-44228`
5. 講：「這是 Log4Shell，CVE-2021-44228，CRITICAL 等級的遠端程式碼執行漏洞。security gate 擋下後，image 沒有推送到 registry，後續部署也不會執行。」
6. **還原**（一定要做）：

   ```bash
   git revert --no-edit HEAD
   git push
   ```

### Demo 3：壞版本自動退版（約 8 分鐘）

**目標：** 放一個會壞的版本 → 自動退回舊版。

1. 先打開 dev 的 `/api/info`，記下 `version`
2. 打開 `k8s/overlays/dev/kustomization.yaml`
3. 把 `DEMO_UNHEALTHY=false` 改成 `DEMO_UNHEALTHY=true`，存檔
4. push：

   ```bash
   git add .
   git commit -m "demo: simulate broken release"
   git push
   ```

5. 等 Deploy to dev 開始後，在 PowerShell 看 Pod：

   ```powershell
   kubectl --context docker-desktop -n rack-dev get pods -w
   ```

   會看到：
   - 舊的一直是 `1/1 Running`
   - 新的一直 `Error` / `CrashLoopBackOff`

   講：「新版 Pod 沒通過 readiness probe；因為 maxUnavailable 是 0，舊版 Pod 不會被終止，服務不中斷。」按 Ctrl+C 結束。
6. 約 2.5 分鐘後：Wait for rollout ❌ → Roll back on failure ✅
7. 再打開 `/api/info` → version 跟第 1 步一樣
8. 講：「rollout status 逾時，pipeline 判定失敗，自動執行 rollout undo；dev 失敗，prod job 也被 skipped。」
9. **還原**（一定要做）：

   ```bash
   git revert --no-edit HEAD
   git push
   ```

### 每個 Demo 之後

revert 後 prod 會停在 Waiting for review。
**一定要按 Approve 或 Reject**，不然下一次 push 會一直卡在排隊（pending）。

### 出狀況時

| 狀況 | 原因 | 處理 |
|---|---|---|
| Deploy to dev 一直 Queued | runner 沒開 | 開 `run.cmd` |
| 新的 run 一直 pending | 前一次停在等核准 | 去前一次按 Approve / Reject |
| port-forward 連不上 | Pod 還沒好 / Docker 沒開 | 等一下，或檢查 Docker Desktop |
| Maven 下載 500 錯誤 | 外部伺服器故障 | 點 Re-run failed jobs |

---

## 8. 面試怎麼講

### 30 秒版

> 我用 GitHub Actions 建了一條完整的 CI/CD pipeline。push 之後，CI 會執行建置和測試、建立 container image，再用 Trivy 做弱點掃描，有 CRITICAL 漏洞就擋下，通過才推送到 GHCR。
>
> CD 由 self-hosted runner 用 kubectl 部署到 Kubernetes：dev 自動部署，prod 需要人工核准。部署採用 rolling update 搭配 readiness probe，rollout 逾時會自動 rollback。

### 加分的真實經驗

> 第一次執行 pipeline 時，Trivy 在 Spring Boot 4.1.1 內建的 Tomcat 11.0.24 掃到 3 個 CRITICAL CVE。我在 pom.xml 覆寫 Tomcat 版本到 11.0.27 修正，而不是用 ignore 清單跳過。

### 常被問

**用了哪些工具？**
GitHub Actions 做 CI/CD、Docker 建立 container image、Trivy 做弱點掃描、GHCR 當 container registry、Kubernetes 做部署，用 Kustomize 管理多環境設定。

**Docker／container 的好處？**
把應用程式和執行環境打包成不可變的 image，開發、測試、正式環境跑的是同一個 image，避免「我電腦上可以跑」的問題。

**Kubernetes 負責什麼？**
container orchestration：維持期望的副本數、Pod 掛掉自動重建、用 rolling update 做不停機更新。

**怎麼確認新版本是健康的？**
readiness probe 定期呼叫 health endpoint，Pod 通過後才接流量；部署後再用 smoke test 從 cluster 內呼叫 API 驗證。

**部署失敗怎麼處理？**
`kubectl rollout status` 設 150 秒 timeout，逾時就觸發 `kubectl rollout undo` 自動 rollback。因為 `maxUnavailable: 0`，過程中舊版持續服務。

**為什麼 prod 要人工核准？**
prod 直接影響使用者，用 environment 的 required reviewers 做 approval gate，在自動化檢查之外，再加一道人工確認。

**dev 和 prod 有什麼差別？**
同一個 image（build once, deploy many），差在 namespace、副本數，以及 ConfigMap 和 Secret 的內容。

**部署在哪？**
練習環境是 Docker Desktop 的本機 Kubernetes。換到雲端（例如 AKS、EKS），manifest 和 pipeline 邏輯不變，主要差在 cluster 認證改用 OIDC，以及對外入口改用 Ingress。

**為什麼用 self-hosted runner？**
cluster 在內網，GitHub-hosted runner 連不進來。self-hosted runner 主動用 outbound HTTPS 向 GitHub 取得 job，不需要開放對外 port。企業內網環境也常用這種方式。

**self-hosted runner 的風險？**
public repo 上，外部 PR 可能在 runner 上執行惡意程式碼，所以設定了外部貢獻者的 PR 需核准才會執行。正式環境應該用專用、權限受限的 runner。

**Secret 怎麼管理？**
存在 GitHub environment secrets，dev 和 prod 各自獨立，部署時才建立成 K8s Secret 注入 container，不進版控。

**image tag 為什麼用 commit SHA？**
SHA 不會被覆蓋，每個 image 都能追溯到對應的 commit，rollback 和排查問題時才準確。不用 `latest`。

### 答不出來時

說實話就好：

> 這部分我還沒深入，這個專案主要是練習 CI/CD 的完整流程。如果工作上需要，我會再研究。

面試官想看的是**你懂整個流程的概念、有親手做過**，不是背參數。

---

## 9. 面試後清理

1. 在 runner 視窗按 Ctrl+C，停止 runner
2. GitHub repo → **Settings → Actions → Runners** → 移除 `jimmy-local`
3. 刪掉 K8s 上的程式：

   ```powershell
   kubectl --context docker-desktop delete namespace rack-dev rack-prod
   ```

4. 不用的話，可以在 Docker Desktop 關掉 Kubernetes，省電腦資源
