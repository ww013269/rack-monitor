# 新手版說明：這個專案到底在做什麼

這份用最白話的方式講，不需要任何背景知識。
想看技術細節再看 [專案導覽](WALKTHROUGH.md)。

**目錄**

1. [三句話講完](#1-三句話講完)
2. [登場角色](#2-登場角色)
3. [C:\actions-runner 是什麼](#3-cactions-runner-是什麼)
4. [一次性設定：我們做了哪些事](#4-一次性設定我們做了哪些事)
5. [push 一次之後，發生了什麼](#5-push-一次之後發生了什麼)
6. [怎麼發現問題、怎麼退版](#6-怎麼發現問題怎麼退版)
7. [Demo 怎麼做](#7-demo-怎麼做)
8. [面試怎麼講](#8-面試怎麼講)
9. [面試後清理](#9-面試後清理)

---

## 1. 三句話講完

1. 我 push 程式碼，GitHub 就會自動**測試、打包、檢查漏洞**。
2. 通過後自動**部署到測試環境（dev）**；**正式環境（prod）要我按同意**才會上。
3. 新版本壞掉的話，會**自動退回舊版**，服務不會中斷。

---

## 2. 登場角色

把整個流程想成「**做便當 → 檢查 → 送到店裡賣**」。

| 角色 | 比喻 | 實際是什麼 |
|---|---|---|
| GitHub repo | 食譜本 | 放程式碼的地方 |
| GitHub Actions | 生產線主管 | 照著步驟清單自動做事 |
| `ci-cd.yml` | 主管的工作清單 | 寫著「先測試、再打包…」 |
| GitHub 雲端機器 | 中央廚房 | 測試、打包都在這裡做 |
| Docker | 封裝機 | 把程式打包成一個「便當盒」 |
| Docker image | 封好的便當 | 程式 + 執行環境，在哪都能跑 |
| Trivy | 食安檢驗員 | 檢查便當裡有沒有壞掉的食材（漏洞） |
| GHCR | 冷凍倉庫 | 存放檢驗合格的便當 |
| runner | 派駐在店裡的員工 | 你電腦上的程式，負責「上架」 |
| Kubernetes（K8s） | 店長 | 管理店面：要擺幾份、壞了換掉 |
| Pod | 架上的一份便當 | 正在執行中的程式 |
| dev / prod | 試吃區 / 正式販售區 | 測試環境 / 正式環境 |

**一句話：** 主管（GitHub Actions）叫中央廚房做便當、檢驗、入庫，再叫店裡的員工（runner）通知店長（K8s）上架。

---

## 3. C:\actions-runner 是什麼

### 白話

**它是 GitHub 派駐在你電腦上的「員工」。**

為什麼需要它？因為你的 K8s 在你電腦裡面，**GitHub 從網路上連不進來**。所以在你電腦上放一個員工，讓**它主動去 GitHub 問有沒有工作**，有的話就在你電腦上執行。

```
GitHub：有一個部署工作喔
   ↑ 每幾秒問一次「有我的工作嗎？」
runner（你電腦上）
   → 領工作 → 執行 kubectl 指令
   → 把結果回報給 GitHub
```

### 資料夾裡有什麼

| 檔案 | 用途 |
|---|---|
| `config.cmd` | **註冊**用：跟 GitHub 說「這台電腦是我的員工」。只執行一次，已經做過了 |
| `run.cmd` | **上班**用：執行後員工開始等工作。視窗關掉就下班 |
| `.runner`、`.credentials` | 註冊後拿到的「員工證」，不要刪、不要給別人 |
| `_work/` | 員工的工作桌，執行工作時的暫存檔放這裡 |
| `bin/`、`externals/` | 員工程式本身 |

### 我們怎麼裝的

1. 從 GitHub 官方下載 runner 的 zip，解壓到 `C:\actions-runner`
2. 跟 GitHub 要一組「註冊碼」
3. 執行 `config.cmd`，用註冊碼註冊，取名 `jimmy-local`，貼上標籤 `local-k8s`
4. 你執行 `run.cmd` → 出現 `Listening for Jobs` = 員工上班了

### 標籤是做什麼的

`deploy.yml` 裡寫著：

```yaml
runs-on: [self-hosted, Windows, local-k8s]
```

意思是：「這個工作只交給**有這三個標籤**的員工」。你的 runner 剛好有，所以部署工作會交給它。

### 你要記得的

- **要部署時，`run.cmd` 視窗必須開著**
- 沒開的話，部署工作會一直排隊等，不會失敗

---

## 4. 一次性設定：我們做了哪些事

這些只要做一次，之後每次 push 都自動跑。

### ① 本機確認程式能跑

- 用 `mvn verify` 編譯 + 跑測試 → 8 個測試全過
- 啟動程式，實際打 API → 都正常

**為什麼：** 先確認程式本身沒問題，之後出錯才知道是流程的問題。

### ② 本機確認能打包

- `docker build` 打包成 image
- `docker run` 跑起來，打 API 正常
- 確認程式不是用管理員（root）身分跑

**為什麼：** 在本機先抓 Dockerfile 的錯，比推上去再等 GitHub 跑快很多。

### ③ 在 GitHub 建 repo

用 `gh repo create` 建立 `ww013269/rack-monitor`。

### ④ 在 GitHub 設定「不能寫在檔案裡的東西」

| 設定 | 為什麼 |
|---|---|
| 建 dev、prod 兩個環境 | 讓部署有「環境」的概念 |
| prod 要你核准 | 正式環境要有人把關 |
| 兩個環境各一把 `API_KEY` 密碼 | 密碼不能寫在程式碼裡，大家都看得到 |
| 陌生人的 PR 要核准才能跑 | 不然別人可以透過 PR 在你電腦上執行指令 |

**為什麼要在 push 之前做：** 不然第一次 push 時，prod 會在沒有核准關卡的狀態下被自動建立。

### ⑤ push 程式碼

`git push` → GitHub 看到 `.github/workflows/ci-cd.yml`，**自動開始跑**。
`ci-cd.yml` 不用另外設定，放在這個資料夾裡 GitHub 就會找到。

這時遇到兩個狀況：

1. **Maven 下載失敗**（伺服器回 500）→ 外部網站暫時故障，重跑就好
2. **Trivy 擋下**：Spring Boot 內建的 Tomcat 有 3 個嚴重漏洞 → 在 `pom.xml` 指定用新版 Tomcat → 通過

### ⑥ 把 image 設成公開

image 存在 GHCR，預設是私人的。設成公開後，K8s 下載 image 時不用帳號密碼。
（只能在 GitHub 網頁上手動改）

### ⑦ 開啟 Kubernetes

Docker Desktop → Settings → Kubernetes → Enable。
就這樣，你電腦裡就有一個小型的 K8s 了。

### ⑧ 安裝 runner

見上面第 3 節。

---

## 5. push 一次之後，發生了什麼

到 GitHub 的 **Actions** 頁面會看到四個方塊，依序執行：

```
[Build & Test] → [Build, Scan & Push] → [Deploy to dev] → [Deploy to prod]
```

### 方塊 1：Build & Test（GitHub 雲端）

1. GitHub 開一台雲端電腦
2. 下載你的程式碼
3. 安裝 Java 17
4. 執行 `mvn verify`：編譯 + 跑 8 個測試

失敗 → 紅叉，後面全部不跑。

### 方塊 2：Build, Scan & Push Image（GitHub 雲端）

1. **打包**：`docker build`，照 Dockerfile 把程式包成 image
2. **檢查**：執行 Trivy。Trivy 本身也是一個 image，臨時下載來用，用完就丟。它把 image 裡每個套件的版本，跟公開的漏洞清單比對
3. **入庫**：通過才 `docker push` 到 GHCR
4. **準備部署設定**：把 K8s 設定檔裡的 image 地址填好，暫存起來給下一步用

Trivy 發現嚴重漏洞 → 紅叉，**image 不會入庫，也不會部署**。

### 方塊 3：Deploy to dev（你電腦上的 runner）

runner 領到工作後：

1. 下載方塊 2 準備好的設定檔
2. 用 GitHub 給的 `API_KEY` 在 K8s 建立密碼
3. `kubectl apply`：把設定檔交給 K8s，意思是「請換成新版本」
4. **K8s 自己去 GHCR 下載 image**（不是 runner 下載的）
5. `kubectl rollout status`：等新版本健康，最多等 150 秒
6. 打一次 API 確認真的能用

### 方塊 4：Deploy to prod（你電腦上的 runner）

1. **停下來等你核准**（畫面顯示 Waiting for review）
2. 你按 Approve → 跟方塊 3 一樣的步驟，部署到 prod

dev 失敗的話，這一步直接跳過。

---

## 6. 怎麼發現問題、怎麼退版

### 發現問題：健康檢查

程式裡有一個網址 `/actuator/health/readiness`，程式正常時會回答「OK」。

K8s **每 5 秒問一次**：

- 回答 OK → 這個 Pod 健康，可以開始服務
- 沒回答 / 報錯 → 不健康，**先不讓它服務**

### 換版本的規則

```
1. 先開一個新版本（舊的不動）
2. 等新的通過健康檢查
3a. 健康 → 關掉舊的 ✅
3b. 不健康 → 舊的不准關，繼續服務
```

所以就算新版本壞掉，**舊版本一直都在服務**，使用者不會受影響。

### 自動退版

runner 執行 `kubectl rollout status` 時一直在等：

- 150 秒內換好 → 成功 ✅
- 150 秒到了還沒換好 → 失敗 ❌ → 自動執行 `kubectl rollout undo` = **退回上一版**

退版後 prod 也不會部署，壞版本完全不會進到正式環境。

---

## 7. Demo 怎麼做

### 先說建議

每次跑完整流程要 **5–10 分鐘**，面試時間寶貴。建議：

1. **主要用已經跑過的紀錄 + 截圖來講**（[專案導覽](WALKTHROUGH.md#實際驗證紀錄)有截圖）
2. 面試官想看現場的，再做 Demo 1

### 面試前準備（提早 30 分鐘做）

**① 打開 Docker Desktop**
等左下角的 Kubernetes 圖示變綠色。

**② 讓 runner 上班**
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
   - Build & Test：「在跑測試」
   - Build, Scan & Push：「打包，用 Trivy 檢查漏洞，通過才存到倉庫」
   - Deploy to dev：「我電腦上的 runner 在部署到 K8s」
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
5. 講：「這是 2021 年很有名的 Log4Shell 漏洞。掃描沒過，image 沒存進倉庫，也沒有部署。」
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

   講：「新版本壞了，但舊版本一直在服務。」按 Ctrl+C 結束。
6. 約 2.5 分鐘後：Wait for rollout ❌ → Roll back on failure ✅
7. 再打開 `/api/info` → version 跟第 1 步一樣
8. 講：「等太久沒成功就自動退版，prod 也不會部署。」
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

> 我做了一個練習專案，用 GitHub Actions 做 CI/CD。push 程式碼後會自動測試、用 Docker 打包，再用 Trivy 檢查漏洞，通過才存到倉庫。之後自動部署到 Kubernetes 的測試環境，正式環境要人工核准。新版本有問題會自動退回舊版。

### 加分的真實經驗

> 第一次跑的時候，Trivy 真的在 Spring Boot 內建的 Tomcat 裡找到嚴重漏洞。我是升級版本修好，不是把警告關掉。

### 常被問

**用了哪些工具？**
GitHub Actions 跑流程、Docker 打包、Trivy 掃漏洞、Kubernetes 部署。

**Docker 是什麼？**
把程式和它需要的環境裝成一個包，在哪裡跑結果都一樣。

**Kubernetes 是什麼？**
管理這些包的系統。會自動重啟壞掉的、換版本時不中斷服務。

**怎麼知道新版本好不好？**
K8s 會定時檢查新版本健康不健康，健康了才換掉舊的。

**部署失敗怎麼辦？**
等太久沒成功，pipeline 會自動退回上一版。

**為什麼正式環境要人工同意？**
正式環境影響使用者，最後一關由人把關比較安全。

**部署在哪？**
練習用的，部署在我電腦上的 Kubernetes。換成雲端，流程和設定檔幾乎一樣，主要是登入叢集的方式不同。

**runner 是什麼？為什麼要用？**
GitHub 派駐在我電腦上的執行程式。因為 K8s 在我電腦裡，GitHub 從外面連不進來，所以讓 runner 主動去 GitHub 領工作。公司內網的環境通常也是這樣做。

**密碼怎麼管？**
存在 GitHub 的密鑰設定，dev 和 prod 各一把，部署時才放進 K8s，不寫在程式碼裡。

### 答不出來時

說實話就好：

> 這部分我還沒深入，這個專案主要是練習 CI/CD 的完整流程。如果工作上需要，我會再研究。

面試官想看的是**你懂整個流程的概念、有親手做過**，不是背參數。

---

## 9. 面試後清理

1. runner 視窗按 Ctrl+C，讓它下班
2. GitHub repo → **Settings → Actions → Runners** → 移除 `jimmy-local`
3. 刪掉 K8s 上的程式：

   ```powershell
   kubectl --context docker-desktop delete namespace rack-dev rack-prod
   ```

4. 不用的話，可以在 Docker Desktop 關掉 Kubernetes，省電腦資源
