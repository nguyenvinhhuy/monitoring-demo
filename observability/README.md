# Observability stack: Prometheus + Grafana + Loki + Tempo (Grafana "LGTM" stack)

Stack học quan sát hệ thống theo đúng 3 trụ cột (metrics/logs/traces) cho 3 service `user-service`,
`product-service`, `order-service` — tất cả gộp trong **1 hệ sinh thái Grafana duy nhất** (không
trộn thêm ELK/Kibana riêng), đúng pattern "Grafana LGTM stack" (Loki + Grafana + Tempo + Mimir/
Prometheus) đang là chuẩn phổ biến hiện nay cho hệ thống bắt đầu từ OpenTelemetry.

## 🗺️ Bản đồ thư mục — file nào làm gì

16 file, dễ ngợp nếu đọc thẳng vào source. Đọc theo **đúng đường đi của 1 request** sẽ dễ hiểu hơn
đọc theo thứ tự alphabet — từ lúc app tạo ra metric/log/trace, tới lúc bạn nhìn thấy nó trên màn
hình:

```
observability/
│
├── otel-collector/                      ── ❶ App đẩy metrics+traces vào đây trước tiên
│   └── otel-collector-config.yaml           nhận OTLP (4317/4318), tách 2 đường: metrics ra
│                                             Prometheus format (8889), traces đẩy tiếp sang Tempo
│
├── prometheus/                          ── ❷ Kho lưu metrics + "bộ não" tính toán/cảnh báo
│   ├── prometheus.yml                        khai báo scrape ai (otel-collector, cadvisor,
│   │                                         node-exporter) + trỏ tới rule_files + alertmanager
│   └── rules/
│       ├── slo.yml                           SLI/SLO/burn-rate — alert CHÍNH (page/ticket)
│       └── services.yml                      ServiceNotReporting/JVMHeap/ContainerCPU — alert PHỤ
│
├── alertmanager/                         ── ❸ Nhận alert từ Prometheus, định tuyến đi đâu
│   └── alertmanager.yml                       route theo severity: page-receiver vs ticket-receiver
│
├── loki/                                 ── ❹ Kho lưu logs
│   └── loki-config.yaml                       storage, schema — nơi Promtail đẩy log vào
├── promtail/                             ── (đi cùng cặp với Loki)
│   └── promtail-config.yaml                   đọc log Docker (qua docker.sock), parse JSON, gắn
│                                             label/structured-metadata, đẩy vào Loki
│
├── tempo/                                ── ❺ Kho lưu traces
│   └── tempo-config.yaml                      nhận OTLP traces từ otel-collector, lưu, phục vụ
│                                             query TraceQL từ Grafana
│
├── grafana/                              ── ❻ Lớp hiển thị — nơi bạn thực sự "nhìn thấy" mọi thứ
│   ├── provisioning/
│   │   ├── datasources/datasource.yml         khai báo Grafana biết nói chuyện với Prometheus/
│   │   │                                     Loki/Tempo nào (xem trang "Connections > Data sources")
│   │   └── dashboards/dashboards.yml          bảo Grafana tự nạp dashboard từ thư mục nào
│   └── dashboards/*.json                      5 dashboard thật (services-red, slo-error-budget,
│                                             jvm-overview, business-orders, infra-containers)
│
├── README.md                             ── file bạn đang đọc: kiến trúc + gotcha thật đã gặp
└── runbooks.md                           ── mỗi alert nổ thì mở mục tương ứng ở đây để biết xử lý sao
```

**Bảng tra nhanh "muốn sửa X thì vào file nào":**

| Muốn làm gì | Sửa file |
|---|---|
| Thêm alert mới / đổi ngưỡng alert | `prometheus/rules/slo.yml` hoặc `services.yml` |
| Đổi kênh nhận alert (Slack/PagerDuty thật) | `alertmanager/alertmanager.yml` |
| Thêm/sửa panel dashboard | `grafana/dashboards/<tên>.json` (nhớ thêm field `datasource` mỗi target — xem gotcha trong README) |
| Thêm datasource Grafana mới | `grafana/provisioning/datasources/datasource.yml` |
| Đổi target Prometheus scrape (thêm service mới) | `prometheus/prometheus.yml` |
| Đổi cách parse log / label Loki | `promtail/promtail-config.yaml` |
| Đổi nơi traces đẩy tới (vd thêm backend khác) | `otel-collector/otel-collector-config.yaml` |
| Alert vừa nổ, không biết xử lý sao | `runbooks.md` (mở đúng mục tên alert) |

Quy tắc chung: mọi thứ trong `observability/` đều được **provisioning bằng file** (không tạo tay
qua UI Grafana/Prometheus) — sửa xong 1 file thì `docker compose restart <service tương ứng>` (hoặc
với Prometheus có thể `curl -X POST localhost:9090/-/reload` khỏi cần restart) để áp dụng.

## Cách chạy

1. Copy `.env.example` thành `.env` ở thư mục gốc repo, điền `GRAFANA_ADMIN_PASSWORD`.
2. `docker compose up -d --build`
3. Các URL:
   - Prometheus: http://localhost:9090
   - Alertmanager: http://localhost:9093
   - Grafana: http://localhost:3000 (đăng nhập bằng `GRAFANA_ADMIN_USER`/`GRAFANA_ADMIN_PASSWORD`) — xem cả metrics, logs, traces ở đây, không cần UI thứ 2
   - Loki: http://localhost:3100 (log, thường không cần mở tay — query qua Grafana Explore)
   - Tempo: http://localhost:3200 (trace, thường không cần mở tay — query qua Grafana Explore)
   - cAdvisor UI: http://localhost:8093
   - OTel Collector: gRPC 4317, HTTP 4318 (nhận OTLP metrics+traces từ app), Prometheus exporter nội bộ tại 8889 (không expose ra host, chỉ Prometheus scrape qua network nội bộ)
   - 3 service: user-service :8090, product-service :8091, order-service :8092 (`/actuator/prometheus` vẫn bật làm escape hatch debug local)

## Kiến trúc: push (app → Collector) vs pull (Prometheus → Collector)

App không biết Prometheus tồn tại — mỗi service chỉ **push** metrics dạng OTLP (`micrometer-registry-otlp`
+ `spring-boot-starter-opentelemetry` đã có sẵn trong pom.xml) tới **OTel Collector** nội bộ
(`otel-collector:4318`). Collector nhận OTLP, gom batch, rồi expose lại dạng Prometheus text
format ở `:8889`. Prometheus chỉ **scrape (pull)** đúng 1 target là Collector.

Lợi ích: tách app khỏi chi tiết backend giám sát. Muốn đổi từ Prometheus sang Thanos/Cortex/Mimir
(để có long-term storage + HA) chỉ cần đổi `exporters` trong
`observability/otel-collector/otel-collector-config.yaml` sang `prometheusremotewrite`, không cần
sửa gì ở code app hay cấu hình Prometheus local.

Đánh đổi: mất đi tín hiệu `up{job=...}` theo từng service mà kiểu scrape trực tiếp có sẵn miễn phí
(Prometheus giờ chỉ biết Collector còn sống hay không, không tự biết `user-service` có đang chạy).
Vì vậy alert `ServiceNotReporting` (xem `rules/services.yml`) dùng `absent_over_time()` trên một
metric nghiệp vụ (`http_server_requests_milliseconds_count{application=...}`) để phát hiện service
ngưng gửi dữ liệu, thay vì dựa vào `up`.

**Tên/đơn vị metric qua Collector đã xác nhận thực tế** (chạy `curl http://otel-collector:8889/metrics`
sau khi tạo vài request/order) — khác với suy đoán ban đầu theo quy ước Prometheus registry thường
dùng:
- `http_server_requests_milliseconds_{count,sum,bucket}` — **milliseconds**, không phải `_seconds`
  như registry Prometheus trực tiếp hay dùng. Bucket `le` cũng theo ms (vd `le="300"` = 300ms).
- Mặc định registry-otlp dùng **base2 exponential histogram**, bỏ qua cấu hình
  `percentiles-histogram`/`slo`. Phải set thêm
  `management.otlp.metrics.export.histogram-flavor: EXPLICIT_BUCKET_HISTOGRAM` (đã áp dụng trong
  `application.yml` 3 service) thì bucket boundary mới khớp với SLO đã khai báo (50/100/200/300/500/1000/2000).
- `orders.created` (Counter) → `orders_created_total{status=...}` (theo đúng quy ước Prometheus counter).
- `orders.value` (DistributionSummary, `baseUnit("VND")`) → `orders_value_VND_{sum,count,bucket}` +
  `orders_value_max_VND` — đơn vị được **chèn vào giữa** tên, không phải hậu tố cuối như suy đoán ban đầu.

Nếu đổi version `otel-collector-contrib` hoặc `micrometer-registry-otlp`, nên chạy lại lệnh `curl`
trên để xác nhận tên/đơn vị chưa đổi trước khi tin vào alert rules/dashboard.

## Logs (Loki + Promtail) và Traces (Tempo) — hoàn thiện nốt 3 trụ cột

Kiến trúc: app → OTel Collector (traces, qua cùng cổng OTLP với metrics) → Tempo. Riêng logs đi
đường khác: 3 service log JSON ra stdout (`logging.structured.format.console: ecs` — Spring Boot tự
serialize, tự gộp MDC), Docker ghi ra file (`json-file` driver), **Promtail** đọc trực tiếp các file
đó qua Docker service discovery (`docker.sock` + mount `/var/lib/docker/containers`) rồi đẩy vào
Loki — không đi qua OTel Collector, vì đơn giản hơn và không cần app đổi gì thêm.

**Correlation logs ↔ traces**: Spring Boot tự thêm `traceId`/`spanId` vào mọi dòng log khi
`micrometer-tracing` đang có trace context active (không cần code thêm gì, tự động từ
`spring-boot-starter-opentelemetry`). Promtail parse 2 field này (+ `correlationId` sẵn có) thành
**structured metadata** trong Loki (query được bằng `| trace_id="..."`, nhưng KHÔNG bị index như
label — nếu đưa vào label sẽ nổ cardinality y hệt lỗi đã tránh ở phần metrics, vì mỗi request có 1
trace_id khác nhau). Datasource Loki có `derivedFields` bắt `"traceId":"..."` trong dòng log, hiện
nút "Xem trace" nhảy thẳng sang Tempo — và ngược lại, Tempo datasource có `tracesToLogsV2` để từ 1
span nhảy ngược lại log liên quan. Đây là điểm mạnh nhất của việc gộp cả 3 trụ cột trong 1 hệ Grafana
thay vì tách rời Kibana/ELK riêng.

**3 lỗi thật đã gặp khi dựng phần này (rất đáng nhớ, dễ lặp lại)**:
1. **Tên field JSON không như ECS chuẩn**: log JSON có `traceId`/`spanId`/`correlationId` là field
   **phẳng ở top-level**, không lồng kiểu `trace.id`/`mdc.correlationId` như ECS thuần — chỉ riêng
   `log.level` là lồng đúng ECS. Cấu hình `pipeline_stages.json.expressions` trong
   `promtail-config.yaml` phải khớp chính xác, kiểm chứng bằng cách xem trực tiếp 1 dòng log JSON
   thật (`docker logs <service> --tail 5`) trước khi viết expression, đừng đoán theo tài liệu ECS.
2. **`management.otlp.tracing.endpoint` KHÔNG kích hoạt trace exporter** dù property này tồn tại
   thật (xác nhận qua `spring-configuration-metadata.json`) — Spring Boot 4.1 có 2 namespace OTLP
   tracing song song, chỉ **`management.opentelemetry.tracing.export.otlp.endpoint`** mới thực sự
   tạo bean `OtlpTracingConnectionDetails`/exporter. Phát hiện được nhờ chạy app với cờ `--debug` và
   đọc "Conditions Evaluation Report" — đây là cách chẩn đoán đáng tin cậy nhất khi 1 property Spring
   Boot "có vẻ đúng tên" nhưng không thấy hiệu lực, thay vì đoán mò tên property khác.
3. **Cách xác minh trace thực sự chạy**: đừng chỉ tin log có `traceId` là đủ (đó chỉ chứng minh
   tracing context tồn tại trong app, không chứng minh đã export đi đâu) — kiểm tra bằng metrics tự
   giám sát của chính OTel Collector (`curl http://otel-collector:8888/metrics` từ 1 container share
   network namespace, vì cổng này chỉ bind `localhost` bên trong): `otelcol_receiver_accepted_spans`
   / `otelcol_exporter_sent_spans` phải > 0. Đây là cách chẩn đoán dứt điểm "có nhận được data không"
   mà không phụ thuộc vào log — hữu ích cho cả metrics/traces/logs pipeline nói chung.

**Demo học tập, chưa phải chuẩn prod đầy đủ**: sample 100% trace (`management.tracing.sampling.probability: 1.0`)
— prod thật cần hạ xuống (adaptive/tail-based sampling) để không tốn chi phí lưu trữ. Loki/Tempo ở
đây dùng storage local filesystem, không phải object storage (S3/GCS/MinIO) như prod cần cho scale
lớn + retention dài hạn.

## SLO, error budget, burn rate — vì sao ưu tiên hơn alert CPU/RAM thô

Alert theo ngưỡng tĩnh (CPU > 80%, heap > 85%) là alert theo **nguyên nhân**, dễ gây alert fatigue vì
không phản ánh trải nghiệm người dùng thật (CPU cao chưa chắc user bị ảnh hưởng). Chuẩn SRE (Google
SRE Workbook) là alert theo **triệu chứng** — dựa trên SLI (Service Level Indicator) so với SLO
(Service Level Objective) và tốc độ đốt error-budget (burn rate):

- **Availability SLO**: 99.5% request thành công / 30 ngày → error budget = 0.5%.
- **Latency SLO**: 95% request nhanh hơn 300ms / 30 ngày → error budget = 5%.
- **Multi-window multi-burn-rate**: mỗi SLO có 2 tầng alert, mỗi tầng xét cùng lúc 2 cửa sổ thời
  gian (ngắn + dài) để vừa bắt được sự cố đột ngột vừa tránh báo động giả do nhiễu ngắn hạn:
  - `severity=page` (fast-burn, cửa sổ 5m + 1h, burn rate > 14.4×): tốc độ lỗi hiện tại nếu tiếp
    diễn sẽ tiêu hết ngân sách 30 ngày trong vài giờ — cần xử lý ngay.
  - `severity=ticket` (slow-burn, cửa sổ 6h + 3d, burn rate > 1×): suy giảm kéo dài nhiều giờ —
    cần điều tra trong ngày, không cần page lúc nửa đêm.
- `JVMHeapUsageHigh`, `ContainerCPUHigh` vẫn giữ lại nhưng chỉ ở mức `ticket` (diagnostic) — dùng để
  tìm nguyên nhân **sau khi** SLO alert đã nổ, không tự page riêng.
- `OtelCollectorDown` / `ServiceNotReporting` vẫn page ngay vì đây là tín hiệu tuyệt đối (mất hoàn
  toàn khả năng quan sát hoặc mất hoàn toàn traffic), không cần chờ tính burn rate.

Alertmanager route theo label `severity`: `page` → receiver `page-receiver` (repeat mỗi 1h),
`ticket` → receiver `ticket-receiver` (repeat mỗi 4h). Demo này chỉ log ra Alertmanager (xem UI
:9093 hoặc `docker compose logs alertmanager`) — file `alertmanager.yml` có comment mẫu cách đổi
sang Slack/PagerDuty thật.

Điểm quan trọng: `severity` được gán theo **mức độ ảnh hưởng người dùng** (burn rate của SLO), không
phải theo "component nào đang lỗi" — đây là lý do route theo label `severity` chứ không route theo
tên alert/service. Page theo triệu chứng (user bị ảnh hưởng), không page theo nguyên nhân (CPU cao
chưa chắc user thấy gì).

Mỗi alert có thêm annotation `dashboard_url` (link Grafana đã lọc sẵn theo đúng `application` gây ra
alert, qua template variable `$application`) và `runbook_url` (trỏ tới [`observability/runbooks.md`](runbooks.md)
— mô tả ý nghĩa, nguyên nhân thường gặp, bước xử lý đầu tiên cho từng alert). Bấm "+ Info" trên
Alertmanager UI để thấy 2 link này cùng `summary`/`description`.

## Ai thực sự xem alert ở đâu (Prometheus UI vs Alertmanager UI vs kênh thông báo thật)

Trong prod thật, người trực (on-call) hầu như không ngồi mở 2 UI này chờ — họ chỉ biết có alert khi
nó đổ về kênh thông báo thật (Slack/PagerDuty/Opsgenie...). Vai trò của từng UI khác nhau rõ rệt:

- **Prometheus `/alerts`**: dành cho người *viết/bảo trì rule*, không phải người trực — xem trạng
  thái `inactive → pending → firing`, có lỗi eval PromQL/label không. Không group/dedupe gì cả, 1
  alert nổ trên N service = N dòng riêng biệt, dễ loạn nếu nhìn lúc có sự cố thật.
- **Alertmanager UI (`:9093`)**: gần với "màn hình vận hành" hơn (đã group theo `group_by`, thấy rõ
  receiver nào nhận gì), nhưng lý do chính người ta mở tay vào đây là để tạo **Silence** (tắt tạm
  alert khi biết trước sắp bảo trì/deploy), không phải để theo dõi liên tục.
- **Nơi thật sự được nhìn hàng ngày**: kênh notification thật (Slack/PagerDuty) cho việc phản ứng
  sự cố, và **Grafana dashboard** (RED, SLO/error-budget) cho việc hiểu "đang xảy ra chuyện gì" —
  không phải Prometheus/Alertmanager UI.
- Demo này chưa nối kênh thật (xem mục "Prod hardening notes" bên dưới), nên tạm thời 2 UI trên là
  cách duy nhất để quan sát alert — đó là lý do các bước ở `verify.md` phải mở trực tiếp 2 UI này.

## Ranh giới business metrics: Prometheus vs Analytics pipeline

Prometheus là time-series DB tối ưu cho **metric tổng hợp, dimensional, cardinality thấp** (đếm/đo
theo một vài tag cố định như `status`, `application`) để phục vụ giám sát vận hành real-time
(dashboard, alert). Nó **không phù hợp** để lưu dữ liệu **per-event, high-cardinality** (tag theo
`orderId`, `userId`, email, tên khách hàng...) — mỗi combination tag tạo ra một time-series riêng,
tag cardinality cao sẽ làm nổ bộ nhớ/số lượng series và làm chậm cả cụm Prometheus.

Trong service này, `orders_created_total` (tag `status`) và `orders_value` chỉ tổng hợp theo vài
trạng thái cố định → an toàn, đúng chuẩn, dùng cho dashboard "orders/phút hiện tại", "tỷ lệ order
thất bại". Nếu sau này cần phân tích chi tiết kiểu BI — doanh thu theo khách hàng/vùng/SKU theo
ngày, phễu chuyển đổi, top khách hàng... — thì đó là bài toán **analytics**, cần pipeline riêng (ghi
event ra Kafka/log rồi đổ vào ELK, ClickHouse, hoặc data warehouse) chứ không nên cố nhồi vào
Prometheus bằng cách thêm tag chi tiết. Phạm vi lần học này dừng ở tầng giám sát vận hành
(Prometheus/Grafana), không dựng thêm pipeline analytics.

## Lưu ý khi chạy trên Docker Desktop for Windows

`node-exporter` và `cadvisor` đọc metrics từ nhân Linux (`/proc`, `/sys`, cgroups). Trên Docker
Desktop for Windows, container thực chạy trong VM Linux (WSL2) — nên các số liệu host/CPU/RAM bạn
thấy là của **VM Linux nền**, không phải trực tiếp phần cứng Windows. Vẫn hữu ích để học cách đọc
dashboard hạ tầng, chỉ cần hiểu đúng bản chất số liệu.

**Giới hạn thật đã gặp khi triển khai (cAdvisor không thấy từng container)**: nếu `docker info` báo
`driver-type: io.containerd.snapshotter.v1` (Docker Desktop bật "Use containerd for pulling and
storing images"), log của `cadvisor` sẽ báo lỗi dạng `failed to identify the read-write layer ID`
cho mọi container, và `container_cpu_usage_seconds_total`/`container_memory_working_set_bytes` chỉ
có data ở mức cgroup gốc (`id="/"`, `id="/docker"`...), không có label theo từng container (`name`,
`image`). Đây là giới hạn đã biết của cAdvisor (được build cho overlay2 graphdriver cổ điển, chưa hỗ
trợ layout containerd snapshotter). Cách khắc phục nếu muốn xem đúng CPU/RAM từng container: vào
Docker Desktop → Settings → General → tắt "Use containerd for pulling and storing images" → restart
Docker Desktop (sẽ chuyển về overlay2 graphdriver cổ điển). Đây là thay đổi ở cấp Docker Desktop,
không phải lỗi trong `docker-compose.yml`/cấu hình cAdvisor — không bắt buộc phải làm để học phần
Prometheus/Grafana/alerting, chỉ ảnh hưởng riêng dashboard `infra-containers`.

## Prod hardening notes (những gì demo này đơn giản hoá, prod thật cần thêm)

- **Auth/TLS**: Grafana/Prometheus/Alertmanager ở đây expose thẳng ra host không qua reverse proxy
  hay TLS — prod cần đặt sau ingress có TLS + auth (OAuth/SSO cho Grafana), không mở port trực tiếp.
- **Long-term storage & HA**: Prometheus/Loki/Tempo ở đây đều **single-instance**, lưu local
  filesystem (Prometheus 15 ngày, Loki/Tempo không giới hạn rõ) — chết 1 container là mất dữ liệu
  gần nhất, không chịu lỗi được. Prod cần remote-write Prometheus sang Thanos/Cortex/Mimir, đổi
  Loki/Tempo sang object storage (S3/GCS/MinIO) + chạy nhiều replica, để vừa lưu dài hạn vừa HA.
- **Resource limits**: docker-compose ở đây chưa set `deploy.resources.limits` — prod cần giới hạn
  CPU/RAM từng container để tránh 1 container ăn hết tài nguyên node.
- **Test cho alert rule như test code**: hiện `slo.yml`/`services.yml` chỉ được verify bằng chaos
  test tay (tắt service, gửi request lỗi, đợi vài phút xem alert nổ) — đúng nhưng chậm và không lặp
  lại tự động được. Prometheus có `promtool test rules`: viết file test khai báo series giả theo
  thời gian + assert alert phải fire/không fire ở mốc nào, chạy trong CI mỗi lần sửa rule, không
  cần dựng cả stack. Nên có trước khi rule phức tạp lên, tránh sửa 1 chỗ gãy chỗ khác mà không biết.
- **Meta-monitoring (ai giám sát người giám sát)**: Prometheus đang tự scrape chính nó (job
  `prometheus`) nhưng chưa có alert/dashboard nào cho sức khoẻ của chính Prometheus/Loki/Tempo —
  vd `prometheus_tsdb_head_series` (cảnh báo cardinality nổ), thời gian scrape, WAL corruption.
  Prod cần ít nhất 1 alert kiểu "Prometheus chính nó có vấn đề" độc lập khỏi rule thường, vì nếu
  Prometheus chết thì mọi alert khác cũng im theo, không ai biết.
- **Blackbox/synthetic monitoring**: toàn bộ stack hiện là **white-box** (app tự báo cáo mình khoẻ
  qua `/actuator/health`, metrics tự export) — chưa có kiểm tra **black-box** từ ngoài vào giống
  user thật (dùng `blackbox_exporter` gọi HTTP vào endpoint public). Khác biệt quan trọng: app có
  thể tự thấy mình khoẻ trong khi load balancer/DNS/network phía trước đã đứt — white-box không bắt
  được trường hợp này, chỉ black-box mới bắt được.
- **RBAC**: Grafana ở đây chỉ 1 tài khoản admin — prod cần SSO + phân quyền viewer/editor/admin theo
  team.
- **Alert routing thật**: `alertmanager.yml` hiện chỉ log — prod cần route `page` sang PagerDuty/
  Opsgenie/VictorOps (app điện thoại vượt Do Not Disturb, rung chuông/gọi thẳng cho on-call) và
  `ticket` sang kênh Slack/Teams (xem lúc rảnh trong giờ làm việc). Vài điểm cần biết thêm:
  - **2 tầng là baseline, không phải luật cứng** — nhiều tổ chức lớn chia 3 tầng: `critical` (page
    ngay) / `warning` (Slack, xem trong giờ) / `info` (chỉ ghi nhận, không thông báo ai, phục vụ
    dashboard/audit). Mở rộng thêm tầng khi 2 tầng không đủ, không cần ép cứng đúng 2 loại.
  - **Chỉ gửi Slack cho tầng `ticket` có rủi ro bị trôi/bỏ sót** nếu kênh bận — nhiều hệ thống
    trưởng thành sẽ tự động tạo **ticket thật** (Jira/ServiceNow) song song với Slack cho tầng này,
    để nó không biến mất nếu không ai đọc kịp.
  - **Giá trị thật của PagerDuty/Opsgenie không chỉ là "vượt DND"** — quan trọng hơn là **escalation
    policy**: page người A trước, nếu sau N phút không ack thì tự động escalate sang người B hoặc cả
    team. Cấu hình escalation là phần cần làm khi nối route thật, không chỉ chọn receiver.
- **Bảo mật scrape endpoint**: `/actuator/prometheus` và Collector `:8889` hiện không auth — prod
  nên giới hạn qua network policy/mTLS giữa Prometheus và target thay vì để mở trong subnet.
