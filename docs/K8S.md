# Kubernetes 實戰說明

說明這個專案在 Kubernetes 裡實際長什麼樣、每個元件在做什麼，以及怎麼現場 demo 給面試官看。
文件裡的輸出都是從本機 cluster 實際執行得到的。

**目錄**

1. [Cluster 是什麼](#1-cluster-是什麼)
2. [資源的層級關係](#2-資源的層級關係)
3. [Namespace：dev 和 prod 怎麼分](#3-namespacedev-和-prod-怎麼分)
4. [Pod：實際在跑的程式](#4-pod實際在跑的程式)
5. [Deployment 與 ReplicaSet](#5-deployment-與-replicaset)
6. [Service：流量怎麼進到 Pod](#6-service流量怎麼進到-pod)
7. [kubectl apply 之後發生什麼](#7-kubectl-apply-之後發生什麼)
8. [Demo：Kubernetes 現場導覽（約 5 分鐘）](#8-demokubernetes-現場導覽約-5-分鐘)
9. [kubectl 指令速查](#9-kubectl-指令速查)
10. [面試問答](#10-面試問答)

---

## 1. Cluster 是什麼

**Cluster（叢集）是一整套 Kubernetes 環境：由一或多台機器（node）組成，K8s 把它們當成一個整體來管理。**

你只要告訴 cluster「我要跑什麼」，它會自己決定放在哪台機器、怎麼啟動，並持續維持。

### Cluster 由兩部分組成

**Control plane（控制平面）**：cluster 的大腦，負責決策。

| 元件 | 作用 |
|---|---|
| `kube-apiserver` | 唯一的入口。kubectl 的所有指令都送到這裡 |
| `etcd` | 資料庫，儲存 cluster 的所有狀態（期望狀態和目前狀態） |
| `kube-scheduler` | 決定新的 Pod 要放在哪個 node |
| `kube-controller-manager` | 持續比對「期望狀態」和「實際狀態」，有差異就修正（例如 Pod 少了就補） |

**Worker node（工作節點）**：實際跑 Pod 的機器。

| 元件 | 作用 |
|---|---|
| `kubelet` | 每個 node 上的代理程式，負責啟動 container、執行健康檢查 |
| container runtime | 實際執行 container（例如 containerd） |
| `kube-proxy` | 處理 Service 的網路轉送 |

### 你的 cluster

```powershell
kubectl --context docker-desktop get nodes
```

```
NAME             STATUS   ROLES           VERSION
docker-desktop   Ready    control-plane   v1.29.1
```

- 是 Docker Desktop 內建的**單節點 cluster**：只有一台 node，同時當 control plane 和 worker
- K8s 版本 1.29.1

control plane 的元件本身也是以 Pod 的形式在跑，放在 `kube-system` namespace：

```powershell
kubectl --context docker-desktop -n kube-system get pods
```

```
coredns-...                       Running   ← cluster 內部 DNS
etcd-docker-desktop               Running
kube-apiserver-docker-desktop     Running
kube-controller-manager-...       Running
kube-proxy-...                    Running
kube-scheduler-docker-desktop     Running
```

### 本機 vs 正式環境

| | 本機（Docker Desktop） | 正式環境（AKS / EKS / GKE） |
|---|---|---|
| Node 數 | 1 台 | 多台，可自動擴充 |
| Control plane | 跟 worker 同一台 | 雲端廠商代管 |
| Node 掛掉 | 整個 cluster 停止 | Pod 自動搬到其他 node |
| 用途 | 開發、練習 | 正式服務 |

**manifest 和 kubectl 的用法完全一樣**，這是 K8s 的好處：本機驗證過的設定，可以直接用在雲端。

---

## 2. 資源的層級關係

```
Cluster
├── Node（機器）：docker-desktop
│
└── Namespace（邏輯隔離空間）
    ├── rack-dev
    │   ├── Deployment: rack-monitor（要 1 個副本）
    │   │   └── ReplicaSet（每個版本一個）
    │   │       └── Pod × 1
    │   │           └── Container: java -jar app.jar
    │   ├── Service: rack-monitor（固定入口）
    │   ├── ConfigMap: rack-monitor-config（APP_ENV=dev）
    │   └── Secret: rack-monitor-secret（API_KEY）
    │
    └── rack-prod
        ├── Deployment: rack-monitor（要 2 個副本）
        │   └── ReplicaSet
        │       └── Pod × 2
        ├── Service / ConfigMap / Secret
```

- **Node** 是實體（或虛擬）機器，**Namespace** 是邏輯分組，兩者是不同維度
- 同一個 namespace 的 Pod 可以分散在不同 node 上

---

## 3. Namespace：dev 和 prod 怎麼分

**本機的 dev 和 prod 在同一個 cluster 裡，用 namespace 隔離。**

```powershell
kubectl --context docker-desktop get namespaces
```

```
NAME              STATUS
default           Active   ← 沒指定 namespace 時的預設
kube-node-lease   Active   ← 系統用
kube-public       Active   ← 系統用
kube-system       Active   ← control plane 元件
rack-dev          Active   ← dev 環境
rack-prod         Active   ← prod 環境
```

兩個 namespace 裡都有叫 `rack-monitor` 的 Deployment 和 Service，**名稱只要在同一個 namespace 內不重複就好**。

### 分辨 dev / prod 的 4 個方法

**① 看 NAMESPACE 欄位**

```powershell
kubectl --context docker-desktop get pods -A
```

`-A`（all namespaces）會列出所有 namespace，第一欄就是環境：

```
NAMESPACE   NAME                            READY   STATUS
rack-dev    rack-monitor-9569f657d-gh492    1/1     Running
rack-prod   rack-monitor-5b889c69d8-fhv5l   1/1     Running
rack-prod   rack-monitor-5b889c69d8-nhmlq   1/1     Running
```

只看單一環境用 `-n`：

```powershell
kubectl --context docker-desktop -n rack-dev get pods
```

**② 看副本數**

```powershell
kubectl --context docker-desktop get deploy -A
```

```
NAMESPACE   NAME           READY
rack-dev    rack-monitor   1/1
rack-prod   rack-monitor   2/2
```

**③ 看 ConfigMap**

```powershell
kubectl --context docker-desktop -n rack-prod get configmap -o yaml
```

dev 是 `APP_ENV: dev`，prod 是 `APP_ENV: prod`。

**④ 問應用程式本身**

```powershell
kubectl --context docker-desktop -n rack-prod port-forward svc/rack-monitor 8082:80
```

瀏覽 http://localhost:8082/api/info → `"env":"prod"`

### 正式環境的做法

namespace 隔離適合練習或小團隊。正式環境通常 **dev 和 prod 用不同的 cluster**，甚至不同的雲端帳號，避免互相影響、權限也更好控管。
這個專案的 `deploy.yml` 有 `kube-context` 參數，要切換到不同 cluster 只需要改這個值。

---

## 4. Pod：實際在跑的程式

**Pod 是 K8s 最小的部署單位。這個專案裡，一個 Pod = 一個 container = 一份正在跑的 Spring Boot 程式。**

### Pod 裡面在跑什麼

```powershell
kubectl --context docker-desktop -n rack-prod exec <pod 名稱> -- ps -eo pid,user,args
```

```
PID  USER  COMMAND
  1  app   java -XX:MaxRAMPercentage=75.0 -jar /app/app.jar
```

- 這就是 Dockerfile 的 `ENTRYPOINT`，也就是你的 Spring Boot 程式
- 執行身分是 `app`（UID 10001），**不是 root**
- **本質上跟你在本機跑 `java -jar` 一樣，只是由 K8s 負責啟動、監控、重啟**

### Pod 用的 image

```powershell
kubectl --context docker-desktop -n rack-prod get pod <pod 名稱> -o jsonpath="{.spec.containers[0].image}"
```

```
ghcr.io/ww013269/rack-monitor:b6b061adb11c32f95f6ea384745d5af2d7a08c35
```

就是 pipeline 推到 GHCR、以 commit SHA 為 tag 的 image。

### Pod 的 log

```powershell
kubectl --context docker-desktop -n rack-prod logs <pod 名稱> --tail=20
```

看到的是 Spring Boot 的輸出，跟本機執行時一樣。

### Pod 的特性

- **短暫的（ephemeral）**：Pod 可能被刪除、重建，**重建後名稱和 IP 都會變**
- 所以不直接連 Pod，而是透過 **Service** 連線
- 也不手動建立 Pod，而是由 **Deployment** 管理

### Pod 名稱的意義

```
rack-monitor-5b889c69d8-fhv5l
└────┬─────┘ └───┬────┘ └─┬─┘
 Deployment   ReplicaSet  Pod 自己
   名稱       （版本代號） 的隨機代號
```

### 為什麼 prod 有 2 個 Pod

這兩個是**同一個程式的副本（replica）**，內容完全一樣：

1. **高可用（high availability）**：一個掛掉，另一個繼續服務；rolling update 時也能逐一替換
2. **負載分散（load balancing）**：Service 把請求分給兩個 Pod

dev 只有 1 個：測試環境不需要高可用，節省資源。

---

## 5. Deployment 與 ReplicaSet

### 管理關係

```
Deployment    你寫的 manifest：「要 2 個副本，用這個 image」
   ↓ 建立、管理
ReplicaSet    負責「維持剛好 N 個 Pod」
   ↓ 建立
Pod × N       實際執行的程式
```

### 每個版本一個 ReplicaSet

```powershell
kubectl --context docker-desktop -n rack-prod get rs
```

```
NAME                      DESIRED   CURRENT   READY
rack-monitor-5b889c69d8   2         2         2      ← 目前版本 b6b061a
rack-monitor-d4656d7bf    0         0         0      ← 舊版本 6ff6047
rack-monitor-d9768648c    0         0         0      ← 舊版本 ca40e1a
```

- 每次部署新版本，Deployment 就會建立一個新的 ReplicaSet
- 舊的 ReplicaSet 不刪除，只是副本數調成 0
- 保留幾個舊版本由 `revisionHistoryLimit: 5` 決定

### Rolling update 的實際過程

部署新版本時，Deployment 逐步調整兩個 ReplicaSet 的副本數：

```
         新 RS   舊 RS
開始       0       2
           1       2    ← maxSurge: 1，先多開 1 個
（新 Pod 通過 readiness probe）
           1       1    ← 才關 1 個舊的
           2       1
（新 Pod 通過 readiness probe）
           2       0    ← 完成
```

`maxUnavailable: 0` 保證 ready 的 Pod 數量永遠不低於 2。

### Rollback 的原理

`kubectl rollout undo` 就是**把舊 ReplicaSet 的副本數調回來、新的調成 0**。
因為舊 ReplicaSet 一直保留著，所以回滾很快。

查看版本歷史：

```powershell
kubectl --context docker-desktop -n rack-prod rollout history deployment/rack-monitor
```

### Self-healing（自我修復）

controller 持續比對：

```
期望狀態：2 個 Pod
實際狀態：1 個（有一個被刪除或當掉）
→ 差異 → 立刻建立 1 個新 Pod
```

這就是**宣告式（declarative）**的核心：你只描述期望狀態，K8s 持續讓實際狀態符合它。

---

## 6. Service：流量怎麼進到 Pod

Pod 會重建、IP 會變，Service 提供**固定的名稱和 IP**。

```powershell
kubectl --context docker-desktop -n rack-prod get svc
```

```
NAME           TYPE        CLUSTER-IP     PORT(S)
rack-monitor   ClusterIP   10.111.45.98   80/TCP
```

### Service 怎麼找到 Pod

Service 用 **label selector** `app: rack-monitor` 找出符合的 Pod，再篩選出 **ready** 的，組成 endpoints 清單：

```powershell
kubectl --context docker-desktop -n rack-prod get endpoints rack-monitor
```

```
NAME           ENDPOINTS
rack-monitor   10.1.0.19:8080,10.1.0.20:8080
```

```
請求 → Service rack-monitor (10.111.45.98:80)
         ├→ Pod 10.1.0.19:8080
         └→ Pod 10.1.0.20:8080
```

**沒通過 readiness probe 的 Pod 不會出現在 endpoints 裡**，所以不會收到流量。Demo C（壞版本）時新 Pod 一直沒進 endpoints，使用者完全不受影響。

### Service 類型

| 類型 | 誰連得到 | 本專案 |
|---|---|---|
| `ClusterIP` | 只有 cluster 內部 | ✅ 使用中 |
| `NodePort` | 透過 node 的某個 port | |
| `LoadBalancer` | 雲端負載平衡器，對外 IP | 正式環境常用 |
| Ingress（另一種資源） | 依網域 / 路徑轉送 HTTP | 正式環境常用 |

因為是 `ClusterIP`，從本機連要用 `kubectl port-forward` 建立臨時通道。
cluster 內部可以直接用名稱 `http://rack-monitor` 連線（由 CoreDNS 解析），smoke test 就是這樣做的。

---

## 7. kubectl apply 之後發生什麼

pipeline 執行 `kubectl apply -f manifests/prod.yaml` 時：

```
1. kubectl ──→ kube-apiserver
   送出 manifest（期望狀態）

2. kube-apiserver ──→ etcd
   儲存期望狀態

3. Deployment controller
   發現 image 改了 → 建立新的 ReplicaSet

4. ReplicaSet controller
   發現 Pod 數不足 → 建立 Pod 物件

5. kube-scheduler
   決定 Pod 放在哪個 node

6. 該 node 的 kubelet
   ├─ 從 GHCR 拉取 image
   ├─ 啟動 container
   └─ 開始執行 readiness / liveness probe

7. Probe 通過
   → Pod 標記為 Ready
   → 加入 Service 的 endpoints，開始接收流量

8. Deployment controller
   依 rolling update 策略縮減舊 ReplicaSet
```

重點：**kubectl 只是提交期望狀態**，後面所有動作都由 K8s 內部的 controller 自動完成。
pipeline 裡的 `kubectl rollout status` 則是在等第 7、8 步完成。

---

## 8. Demo：Kubernetes 現場導覽（約 5 分鐘）

這個 demo **不需要等 pipeline**，每個指令都是立即反應，很適合在面試中現場展示。

### 事前準備

1. Docker Desktop 已開啟，Kubernetes 是綠燈
2. 開一個 PowerShell 視窗，先設定縮寫（之後指令比較短）：

   ```powershell
   function k { kubectl --context docker-desktop @args }
   ```

   之後 `k get pods` = `kubectl --context docker-desktop get pods`
   （關掉視窗後縮寫就失效，下次要重設；含 `--` 的 `exec` 指令例外，要打完整的 kubectl）

3. 把字體放大，方便對方在螢幕分享時看清楚

### 步驟 1：介紹 cluster（30 秒）

```powershell
k get nodes
```

> 「這是 Docker Desktop 內建的單節點 Kubernetes cluster，版本 1.29。正式環境會是多節點的雲端 cluster，但 manifest 和操作方式一樣。」

### 步驟 2：環境隔離（30 秒）

```powershell
k get pods -A | Select-String rack
```

> 「dev 和 prod 用 namespace 隔離。dev 跑 1 個副本，prod 跑 2 個做高可用。正式環境我會用不同的 cluster 來隔離。」

### 步驟 3：同一個 image（30 秒）

```powershell
k get deploy -A -o wide | Select-String rack
```

指著 `IMAGES` 欄位：

> 「dev 和 prod 跑的是同一個 image，tag 是 commit SHA。這就是 build once, deploy many：測過的 image 就是上線的 image，只有 ConfigMap 和 Secret 不同。」

（如果 dev 和 prod 的版本不一樣，代表最後一次 prod 部署沒有核准，可以順便說明 approval gate。）

### 步驟 4：Pod 裡面是什麼（1 分鐘）

先取得一個 prod Pod 的名稱：

```powershell
k -n rack-prod get pods
```

複製其中一個名稱，替換下面的 `<pod>`：

```powershell
kubectl --context docker-desktop -n rack-prod exec <pod> -- ps -eo pid,user,args
```

⚠️ `exec` 這一行要用完整的 `kubectl`，不能用 `k` 縮寫：PowerShell 的函式會把 `--` 吃掉，指令就會出錯。

> 「每個 Pod 跑一個 container，裡面就是 Spring Boot 程式。可以看到執行者是 app 使用者，不是 root，這是在 Dockerfile 和 securityContext 都有設定的。」

```powershell
k -n rack-prod logs <pod> --tail=5
```

> 「log 直接寫到 stdout，K8s 會收集。正式環境會再接到集中式 log 系統。」

### 步驟 5：Service 與健康檢查（1 分鐘）

```powershell
k -n rack-prod get endpoints rack-monitor
```

> 「Service 提供固定入口，目前把流量分給這兩個 Pod 的 IP。只有通過 readiness probe 的 Pod 才會出現在這裡，所以啟動中或有問題的 Pod 不會收到流量。」

### 步驟 6：Self-healing（1 分鐘）⭐ 最有效果

開**第二個** PowerShell 視窗，持續觀察 Pod（同樣要先設定 `k` 縮寫）：

```powershell
function k { kubectl --context docker-desktop @args }
k -n rack-prod get pods -w
```

回到第一個視窗，刪除一個 Pod：

```powershell
k -n rack-prod delete pod <pod>
```

第二個視窗會看到：

```
<被刪的 pod>   1/1   Terminating
<新的 pod>     0/1   Pending
<新的 pod>     0/1   ContainerCreating
<新的 pod>     0/1   Running      ← 啟動中，還沒通過 readiness
<新的 pod>     1/1   Running      ← 通過 readiness，開始接流量
```

> 「我刪掉一個 Pod，ReplicaSet 發現實際數量少於期望的 2 個，馬上建立新的。新 Pod 要通過 readiness probe 才會接流量，這段期間另一個 Pod 持續服務，所以沒有中斷。這就是宣告式管理：我只定義期望狀態，K8s 負責維持。」

### 步驟 7：擴展（30 秒）

```powershell
k -n rack-prod scale deployment/rack-monitor --replicas=3
```

第二個視窗會看到第 3 個 Pod 出現。

> 「流量變大時可以調整副本數。正式環境會用 HPA 依 CPU 使用率自動調整。」

**改回來**（一定要做）：

```powershell
k -n rack-prod scale deployment/rack-monitor --replicas=2
```

> 補充：手動 scale 只是暫時的，下次 pipeline 部署會依 manifest 恢復成 2 個。設定以 Git 裡的 manifest 為準。

### 步驟 8：版本歷史與回滾（30 秒）

```powershell
k -n rack-prod get rs
k -n rack-dev rollout history deployment/rack-monitor
```

> 「每次部署都會產生新的 ReplicaSet，舊的保留下來，副本數為 0。rollback 就是把舊 ReplicaSet 的副本數調回來，所以很快。pipeline 在 rollout 逾時時會自動執行 rollout undo。」

### 結束

在第二個視窗按 **Ctrl+C** 停止觀察。

---

## 9. kubectl 指令速查

以下都要加 `--context docker-desktop`（或用上面的 `k` 縮寫）。

**查看**

| 指令 | 用途 |
|---|---|
| `get nodes` | 列出 node |
| `get ns` | 列出 namespace |
| `get pods -A` | 所有 namespace 的 Pod |
| `-n rack-prod get pods` | 指定 namespace 的 Pod |
| `get pods -o wide` | 多顯示 IP、node |
| `get pods -w` | 持續觀察變化（Ctrl+C 結束） |
| `get deploy,rs,svc` | 一次看多種資源 |
| `get endpoints rack-monitor` | Service 目前導流量給哪些 Pod |
| `describe pod <pod>` | 詳細資訊和事件，**排查問題最常用** |
| `logs <pod>` | 看 log |
| `logs <pod> --previous` | 看當掉前那一次的 log |

**操作**

| 指令 | 用途 |
|---|---|
| `exec <pod> -- <指令>` | 在 container 內執行指令 |
| `port-forward svc/rack-monitor 8082:80` | 從本機連到 Service |
| `delete pod <pod>` | 刪除 Pod（會被自動重建） |
| `scale deployment/rack-monitor --replicas=3` | 調整副本數 |
| `rollout status deployment/rack-monitor` | 等待更新完成 |
| `rollout history deployment/rack-monitor` | 版本歷史 |
| `rollout undo deployment/rack-monitor` | 回滾到上一版 |

**排查 Pod 起不來時**

1. `get pods` → 看 STATUS（`CrashLoopBackOff`、`ImagePullBackOff`…）
2. `describe pod <pod>` → 看最下面的 Events
3. `logs <pod> --previous` → 看程式當掉前的錯誤訊息

---

## 10. 面試問答

**Cluster 是什麼？**
一組由 K8s 統一管理的機器。control plane 負責決策（API server、etcd、scheduler、controller），worker node 負責實際執行 Pod。

**Pod 是什麼？**
K8s 最小的部署單位，包含一或多個 container。這個專案每個 Pod 跑一個 Spring Boot container。

**Pod 和 container 的差別？**
container 是執行中的 image；Pod 是 K8s 管理的單位，可以包含多個共用網路和儲存的 container（例如主程式加上 log 收集的 sidecar）。

**為什麼不直接建立 Pod，要用 Deployment？**
Pod 本身掛了不會重建。Deployment 透過 ReplicaSet 維持副本數，並提供 rolling update 和 rollback。

**Deployment 和 ReplicaSet 的關係？**
Deployment 管理版本，每個版本對應一個 ReplicaSet；ReplicaSet 負責維持 Pod 數量。rolling update 就是逐步調整新舊 ReplicaSet 的副本數。

**Service 的作用？**
Pod 會重建、IP 會變，Service 提供固定的名稱和 IP，用 label selector 找到 Pod，只把流量導給 ready 的 Pod。

**Readiness 和 liveness probe 的差別？**
readiness 失敗 → 從 Service endpoints 移除，不接流量；liveness 失敗 → 重啟 container。

**什麼是宣告式管理？**
只描述期望狀態（要幾個副本、用哪個 image），K8s 的 controller 持續讓實際狀態符合期望。相對的是命令式：一步步下指令。

**Namespace 的用途？**
cluster 內的邏輯隔離，可以分別設定權限（RBAC）和資源配額。這個專案用來隔離 dev 和 prod。

**Pod 一直 CrashLoopBackOff 怎麼排查？**
`describe pod` 看 Events，`logs --previous` 看當掉前的錯誤。常見原因：設定錯誤、缺少環境變數或 Secret、記憶體不足被 OOMKilled、liveness probe 太早開始檢查。

**怎麼做到更新不停機？**
rolling update 搭配 `maxUnavailable: 0` 和 readiness probe：新 Pod ready 之後才移除舊 Pod，Service 只導流量給 ready 的 Pod。
