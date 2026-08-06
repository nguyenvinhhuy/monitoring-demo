## Prometheus + Grafana trả lời cho câu hỏi: "CÁI GÌ ĐANG XẢY RA?"
Bộ đôi này tập trung vào hiện tượng và hiệu năng tổng thể theo thời gian thực.
- Hệ thống có đang chạy ổn định không?
- Tải CPU và RAM của máy chủ hiện tại là bao nhiêu %?
- Số lượng người dùng truy cập cùng lúc (RPS) tăng hay giảm?
- Hệ thống có bị quá tải hoặc phản hồi chậm không?
- Khi nào cần phát báo động?

## Bước 1: Kiểm tra kết nối hạ tầng (Target Status) 
Trước tiên, bạn cần đảm bảo Prometheus đã tìm thấy và kết nối thành công tới OTel Collector cùng các công cụ thu thập hạ tầng khác.
1. Mở trình duyệt và truy cập vào đường dẫn: http://localhost:9090/targets
2. Kiểm tra danh sách các job hiển thị trên màn hình.
3. Điều kiện đạt: Tất cả các endpoint thuộc các job sau đây đều phải hiển thị trạng thái màu xanh UP:
   1. otel-collector (Cổng 8889)
   2. cadvisor (Cổng 8080)
   3. node-exporter (Cổng 9100)
   4. prometheus (Cổng 9090 - tự giám sát)

## Bước 2: Truy vấn Metric gốc trên Prometheus UI
Bước này giúp xác nhận dữ liệu từ các ứng dụng Java đã đẩy thành công qua OTel Collector về tới kho lưu trữ của Prometheus.
1. Tại giao diện Prometheus (http://localhost:9090), chuyển sang tab Graph.
2. Nhập tên metric sau vào khung tìm kiếm và ấn Execute:
   1. promql `http_server_requests_milliseconds_count`
    (Đã xác nhận thật bằng `curl http://otel-collector:8889/metrics`: registry-otlp xuất đơn vị **milliseconds**, không phải `_seconds` như registry Prometheus trực tiếp hay dùng. Nếu sau này đổi version `micrometer-registry-otlp`/`otel-collector-contrib` mà không ra kết quả, chạy lại lệnh curl trên để xác nhận tên mới.)
3. Điều kiện đạt: Kết quả trả về danh sách các bản ghi kèm theo nhãn phân biệt rõ ràng tên của cả 3 ứng dụng qua label service_name hoặc job (ví dụ: user-service, order-service, product-service).
4. Tiếp tục tìm kiếm metric business tự định nghĩa bằng cách gõ orders_created_total. Hệ thống phải trả về dữ liệu đi kèm các nhãn status="CREATED" hoặc status="FAILED".
5. (Tuỳ chọn, kiểm tra sâu hơn) Gõ `http_server_requests_milliseconds_bucket` — phải thấy các `le` đúng với SLO đã khai báo trong `application.yml` (50, 100, 200, 300, 500, 1000, 2000). Nếu chỉ thấy toàn `le` lẻ kiểu 1.048576, 2.097151... nghĩa là registry đang rơi về base2 exponential histogram mặc định — kiểm tra lại `management.otlp.metrics.export.histogram-flavor: EXPLICIT_BUCKET_HISTOGRAM` có còn trong `application.yml` của cả 3 service không.

## Bước 3: Kiểm tra tính toán SLO (Recording Rules)
Vì các cảnh báo chính phụ thuộc vào file cấu hình slo.yml bạn đã viết, bạn cần verify xem Prometheus có tính toán mượt mà các luật này hay không. SLO có 2 trụ cột (availability + latency) — phải check cả hai, thiếu latency là thiếu một nửa thiết kế.
1. Tại khung tìm kiếm của Prometheus UI, nhập lần lượt 2 Recording Rule:
   1. promql `sli:availability:ratio_rate5m`
   2. promql `sli:latency:ratio_rate5m`
2. Bấm Execute rồi chuyển sang tab Graph để xem đồ thị (cho cả 2 query).
3. Điều kiện đạt: Biểu đồ hiển thị giá trị dao động quanh 1 (100%, không lỗi/không chậm). Nếu hệ thống chạy mượt không lỗi và không có request nào >300ms, giá trị trả về phải là 1. Lưu ý: nếu trước đó bạn từng test bằng request lỗi/sai định dạng (như phần lỗi 500 do `productId` gửi sai kiểu ở order-service), giá trị `sli:availability` sẽ thấp hơn 1 một cách chính đáng — đó là hệ thống đang phản ánh đúng lỗi thật, không phải bug của recording rule.

## Bước 4: Kiểm tra Dashboard tự động trên Grafana
Grafana là lớp hiển thị cuối cùng. Bạn cần verify xem cơ chế tự động nạp dữ liệu (provisioning) có hoạt động chính xác không mà không cần cấu hình bằng tay.
1. Truy cập vào http://localhost:3000 và đăng nhập bằng tài khoản Admin — lấy `GRAFANA_ADMIN_USER`/`GRAFANA_ADMIN_PASSWORD` từ file `.env` (copy từ `.env.example`). Mặc định trong `.env.example` là `admin` / `change-me`, **không phải** `admin/admin`.
2. Vào mục Dashboards > Tìm kiếm thư mục "Monitoring Demo" chứa 5 dashboard bạn đã khai báo trong file cấu hình (services-red, slo-error-budget, jvm-overview, business-orders, infra-containers).
   1. Business - Orders: Dashboard theo dõi số lượng đơn hàng, doanh thu và lỗi nghiệp vụ theo thời gian thực.
   2. Infra - Containers & Host: Dữ liệu phần cứng từ cAdvisor và Node Exporter.
   3. JVM Overview: Sức khỏe phần mềm Java (Heap, GC Pause, Thread).
   4. Services - RED (Rate / Errors / Duration): Ba chỉ số vàng của vận hành hệ thống.
   5. SLO & Error Budget: Bản đồ theo dõi tốc độ đốt ngân sách lỗi (Burn-rate) theo công thức Google SRE Workbook.
3. Mở lần lượt cả 5 dashboard (không chỉ 2 cái), đặc biệt để ý slo-error-budget và business-orders.
4. Điều kiện đạt:
   1. Tất cả các panel (ô hiển thị) đều vẽ được đồ thị hoặc hiển thị số lượng cụ thể, không có ô nào bị báo lỗi đỏ (nhấp nháy chữ i/error).
   2. Khi bạn thực hiện gọi API tạo đơn hàng (gọi thử vài request tạo order thành công và thất bại), các biểu đồ trên dashboard business-orders lập tức nhảy số nảy lên theo thời gian thực (Real-time).
   3. **Ngoại lệ đã biết — dashboard Infra - Containers & Host**: panel CPU/Memory theo container có thể "No data" hoặc chỉ hiện 1 series gộp (cgroup gốc `id="/"`) thay vì tách theo từng container — đây là giới hạn thật của cAdvisor trên Docker Desktop dùng containerd snapshotter (kiểm tra bằng `docker info | grep driver-type`; nếu ra `io.containerd.snapshotter.v1` thì đúng trường hợp này), không phải lỗi cấu hình. Xem `observability/README.md` mục cAdvisor để biết cách khắc phục (tắt "Use containerd for pulling and storing images" trong Docker Desktop settings) — không bắt buộc phải sửa để coi là "đạt".
   4. Panel host load/memory (node-exporter) trên dashboard Infra vẫn có data bình thường, nhưng nếu chạy trên Docker Desktop for Windows thì đó là số liệu của **VM Linux nền (WSL2)**, không phải phần cứng Windows thật — không tính là sai, chỉ cần hiểu đúng bản chất số liệu khi đọc.
5. **Gotcha đã gặp thật — "Data source not found" (404) khi mở dashboard**: nếu panel báo lỗi này (xem log Grafana: `docker compose logs grafana | grep "data source not found"`), nguyên nhân thường là dashboard JSON thiếu field `datasource` tường minh trong từng target (Grafana 11 không tự fallback về datasource mặc định như bản cũ). Sau khi sửa file dashboard JSON và Grafana đã provisioning lại (`docker compose restart grafana`), **trình duyệt đang mở sẵn dashboard đó vẫn có thể còn báo lỗi cũ** vì Grafana chỉ tự refresh *dữ liệu* mỗi khi đến giờ (`refresh: 30s`), không tự tải lại *định nghĩa dashboard* — phải **Ctrl+Shift+R (hard reload)** hoặc đóng mở lại tab thì mới thấy bản mới. Xác nhận server đã đúng bằng `curl -u admin:<password> http://localhost:3000/api/dashboards/uid/<uid>` xem field `datasource` của từng target trước khi kết luận lỗi do đâu.

## Bước 5: Kiểm tra Logs (Loki) và Traces (Tempo) qua Grafana Explore

2 phần này không có dashboard riêng (Explore là công cụ tra cứu tự do, không cần dashboard) — trả lời câu hỏi "chính xác request này lỗi ở đâu, log gì, đi qua những service nào" mà Prometheus/Grafana ở Bước 1-4 (chỉ trả lời "có vấn đề không") không làm được.

**Kiểm tra Loki (logs):**
1. Vào **Explore** (icon la bàn ở sidebar trái, hoặc `http://localhost:3000/explore`), chọn datasource **Loki**.
2. Gõ LogQL: `{compose_service="order-service"}` → Run query (Shift+Enter).
3. Điều kiện đạt: thấy log JSON hiện ra, panel "Logs volume" phía trên vẽ được biểu đồ theo level (debug/info/warning/error).
4. Lọc theo mức lỗi: `{compose_service="order-service", level="ERROR"}`.
5. Lọc theo 1 trace cụ thể (structured metadata, KHÔNG phải label — tra bằng `|`, không phải `{}`): `{compose_service="order-service"} | trace_id="<traceId>"`.
6. Expand 1 dòng log có field `traceId` → phải thấy nút **"Xem trace"** (derived field) bấm nhảy thẳng sang Tempo.
7. Không nhớ label nào có sẵn: bấm **"Label browser"** cạnh ô query (sẽ thấy `compose_service`, `level`, `container`, `stream`...).

**Kiểm tra Tempo (traces):**
1. Đổi datasource sang **Tempo**, tab **Search** (dễ hơn TraceQL cho người mới) → chọn `Service Name = order-service` → Run query → ra danh sách trace dạng bảng, bấm vào 1 dòng để xem waterfall.
2. Hoặc dùng TraceQL: `{resource.service.name="order-service"}`.
3. Điều kiện đạt: 1 trace tạo order phải có **7 span** trải trên cả 3 service — gốc là `http post /api/orders` (order-service), 2 nhánh con gọi sang `user-service` (`http get /api/users/{id}`) và `product-service` (`http get /api/products/{id}` + `http post .../reserve`), đúng parent-child, đúng thứ tự thời gian.
4. Lưu ý: đa số trace trong danh sách sẽ là `http get /actuator/health` (Docker healthcheck tự gọi mỗi 10s) — đó là nhiễu bình thường, không phải lỗi; lọc theo `rootTraceName` hoặc tìm dòng có tên `http post /api/orders` để thấy trace nghiệp vụ thật.
5. Từ 1 span trong waterfall, bấm nút nhảy ngược sang **Loki** xem log của đúng request đó (`tracesToLogsV2`).

**3 gotcha thật đã gặp khi dựng phần này — rất đáng nhớ khi training người khác:**
1. **`management.otlp.tracing.endpoint` KHÔNG kích hoạt trace exporter** dù property này tồn tại thật (có trong `spring-configuration-metadata.json`) — Spring Boot 4.1 có 2 namespace OTLP tracing song song, chỉ **`management.opentelemetry.tracing.export.otlp.endpoint`** mới thực sự tạo bean exporter. Chỉ phát hiện được bằng cách chạy app với cờ `--debug` và đọc "Conditions Evaluation Report" — nếu 1 property "nhìn có vẻ đúng tên" mà không thấy hiệu lực, đây là cách chẩn đoán đáng tin cậy nhất, đừng đoán tên khác.
2. **Field JSON log là `traceId`/`spanId`/`correlationId` phẳng ở top-level**, không lồng kiểu ECS chuẩn `trace.id`/`mdc.correlationId` — chỉ riêng `log.level` là lồng đúng ECS. Luôn xem 1 dòng log JSON thật (`docker logs <service> --tail 5`) trước khi viết `pipeline_stages.json.expressions` trong Promtail, đừng đoán theo tài liệu ECS thuần.
3. **Cách xác minh trace thực sự được export**, không chỉ tin log có `traceId` (đó chỉ chứng minh tracing context tồn tại trong app, không chứng minh đã gửi đi đâu): kiểm tra metrics tự giám sát của OTel Collector — `docker run --rm --network container:monitoring-demo-otel-collector-1 curlimages/curl -s http://127.0.0.1:8888/metrics | grep span` (cổng 8888 chỉ bind `localhost` bên trong container nên phải share network namespace mới gọi được) — `otelcol_receiver_accepted_spans`/`otelcol_exporter_sent_spans` phải > 0.
4. **Đừng nhầm `correlationId` với `traceId`** khi copy tay để tra Tempo — 2 field đứng cạnh nhau trong log JSON nhưng giá trị khác nhau hoàn toàn (traceId đúng dùng để tra Tempo/TraceQL, correlationId là ID tự sinh riêng của app dùng để nối log — không tồn tại bên Tempo). Dán nhầm sẽ ra lỗi `404 Not Found`, dễ tưởng nhầm là bug.

## 🧪 Mẹo tạo "Lỗi Giả" để test Alert nổ (Chaos Engineering nhẹ)
Để thấy được Alert đổ về Alertmanager UI (http://localhost:9093) hoạt động đúng thiết kế 2 tầng (`severity=page` vs `severity=ticket`):

**1. Tắt cứng service (test tầng `page` — mất khả năng quan sát/traffic)**
- Chạy lệnh `docker compose stop user-service`.
- Alert đúng tên cần theo dõi trên tab Alerts của Prometheus là **`ServiceNotReporting`** (không phải `ServiceDown` — tên đó không tồn tại trong hệ thống này). Lý do: kiến trúc push (app → OTel Collector) không có `up{job=...}` theo từng service như kiểu scrape trực tiếp, nên alert phải dùng `absent_over_time()` trên metric nghiệp vụ để phát hiện service ngưng báo cáo — xem `observability/README.md` mục "push vs pull".
- **Thời gian thực tế không phải "ngay lập tức"** — đã tự đo: khoảng **~4 phút** kể từ lúc `stop` đến khi alert chuyển `Pending -> Firing` (30 giây để OTel Collector hết hạn giữ metric cũ + 3 phút cửa sổ `absent_over_time` + 1 phút `for`). Đừng tưởng bị treo nếu đợi vài chục giây chưa thấy đổi màu.
- Sau khi thấy `ServiceNotReporting` (severity=page) firing ở Prometheus, kiểm tra tiếp http://localhost:9093 — alert phải xuất hiện ở đây với label `severity="page"`.

**2. Bào mòn SLO (test tầng `page` — availability SLO)**
- Khi `user-service` đã tắt, liên tục gửi API tạo đơn hàng mới sang order-service. order-service gọi sang user bị lỗi, trả về lỗi nội bộ liên tục.
- Alert `AvailabilitySLOFastBurn` (severity=page) sẽ chuyển firing sau khoảng 2 phút (`for: 2m`) kể từ khi burn rate vượt ngưỡng liên tục.

**3. Kiểm tra tầng `ticket` (diagnostic, KHÔNG page) có route khác `page` không**
Phần này bị thiếu hoàn toàn ở bản gốc — nếu chỉ test được tầng `page` thì chưa verify được đúng thiết kế "2 tầng" mà file này đặt tên. Cách dễ nhất để thấy tầng `ticket` mà không cần giả lập CPU/heap cao (khó tái tạo chủ động):
- Gửi một lượng nhỏ request lỗi/chậm rải rác trong nhiều giờ (thay vì dồn dập) — đủ để `AvailabilitySLOSlowBurn`/`LatencySLOSlowBurn` (đều gắn `severity=ticket`) chuyển firing (`for: 15m`), nhưng KHÔNG đủ để `AvailabilitySLOFastBurn` (`severity=page`) kích hoạt.
- Tại http://localhost:9093, xác nhận alert `severity=ticket` xuất hiện dưới receiver `ticket-receiver` còn alert `severity=page` xuất hiện dưới `page-receiver` (2 nhóm tách biệt, thấy rõ qua route/label trong Alertmanager UI).
- (Tuỳ chọn, khó tái tạo chủ động hơn) `JVMHeapUsageHigh`/`ContainerCPUHigh` cũng gắn `severity=ticket` — chỉ tự nổ khi heap/CPU container thật sự cao liên tục >10 phút, không cần thiết phải ép test bằng tay.

**4. Dọn dẹp sau khi test xong**
- Chạy `docker compose start user-service` để khôi phục lại stack về trạng thái healthy trước khi tiếp tục việc khác.