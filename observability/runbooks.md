# Runbooks

Mỗi alert trong `observability/prometheus/rules/*.yml` có annotation `runbook_url` trỏ về đúng mục
tương ứng ở file này (vd `observability/runbooks.md#availabilityslofastburn`). Trong demo này
`runbook_url` chỉ là **path tương đối tới file trong repo** — không phải link bấm-là-mở được từ
Alertmanager UI (vì không có server host file này). Ở prod thật, giá trị này sẽ là URL sống trỏ tới
trang wiki nội bộ (Confluence/Notion/backstage...) để bấm thẳng từ Alertmanager/PagerDuty ra được.

## AvailabilitySLOFastBurn

**Ý nghĩa**: tốc độ lỗi (`SERVER_ERROR`) hiện tại, nếu tiếp diễn, sẽ tiêu hết error-budget 30 ngày
trong vài giờ. Đây là alert `severity=page` — cần xử lý ngay.

**Nguyên nhân thường gặp**: service phụ thuộc (user-service/product-service) down hoặc lỗi, bug mới
deploy gây exception hàng loạt, request client gửi sai định dạng bị map nhầm thành 500 thay vì 400
(xem ví dụ thật đã gặp: `productId` gửi dạng chuỗi thay vì số ở order-service).

**Bước xử lý đầu tiên**:
1. Mở `dashboard_url` trong annotation (dashboard `slo-error-budget`, đã lọc theo đúng service) để
   xem burn rate hiện tại và service nào đang gọi lỗi.
2. Xem log gần nhất của service đó (`docker compose logs -f <service>`) để tìm exception cụ thể.
3. Nếu là do service phụ thuộc down → xử lý theo [ServiceNotReporting](#servicenotreporting).
4. Nếu là do bug mới deploy → rollback trước, điều tra sau.

## AvailabilitySLOSlowBurn

**Ý nghĩa**: suy giảm availability kéo dài nhiều giờ nhưng chưa tới mức khẩn cấp. `severity=ticket`
— không page, xử lý trong giờ làm việc.

**Nguyên nhân thường gặp**: lỗi rải rác không liên tục (vd một endpoint ít dùng bị lỗi), tải tăng dần
theo thời gian, hoặc dư âm của một đợt lỗi ngắn đã qua (cửa sổ 6h/3d vẫn còn tính vào).

**Bước xử lý đầu tiên**:
1. Mở `dashboard_url` (dashboard `slo-error-budget`, khung 3 ngày) để xem xu hướng.
2. So sánh với `AvailabilitySLOFastBurn` — nếu alert đó cũng đang firing thì đây chỉ là hệ quả, ưu
   tiên xử lý fast-burn trước.
3. Nếu là lỗi rải rác thật, tạo ticket điều tra root cause, không cần phản ứng ngay trong đêm.

## LatencySLOFastBurn

**Ý nghĩa**: tỷ lệ request chậm hơn 300ms tăng đột biến, đang đốt error-budget latency rất nhanh.
`severity=page`.

**Nguyên nhân thường gặp**: database chậm (connection pool cạn, query chậm), GC pause dài (xem
[JVMHeapUsageHigh](#jvmheapusagehigh)), service phụ thuộc phản hồi chậm, CPU container bị nghẽn (xem
[ContainerCPUHigh](#containercpuhigh)).

**Bước xử lý đầu tiên**:
1. Mở `dashboard_url` (`slo-error-budget`) xem p95/p99 hiện tại so với SLO 300ms.
2. Mở dashboard `jvm-overview` cho cùng service — kiểm tra heap usage và GC CPU time có tăng bất
   thường không.
3. Kiểm tra HikariCP pool (`hikaricp_connections_pending`) qua `/actuator/prometheus` của service đó
   — pending tăng nghĩa là nghẽn ở tầng DB.

## LatencySLOSlowBurn

**Ý nghĩa**: suy giảm latency kéo dài nhiều giờ, chưa khẩn cấp. `severity=ticket`.

**Bước xử lý đầu tiên**: tương tự LatencySLOFastBurn nhưng không cần phản ứng ngay — ghi nhận, xem
xu hướng dashboard `slo-error-budget` khung 3 ngày, lên kế hoạch tối ưu trong sprint.

## OtelCollectorDown

**Ý nghĩa**: Prometheus không scrape được `otel-collector` — mất toàn bộ khả năng quan sát của cả 3
service (không phải chỉ 1 service lỗi, mà là **mù hoàn toàn**). `severity=page`.

**Bước xử lý đầu tiên**:
1. `docker compose ps otel-collector` — container có đang chạy không.
2. `docker compose logs otel-collector` — tìm lỗi khởi động/crash.
3. Nếu container chết, `docker compose up -d otel-collector` để khởi động lại; 3 app sẽ tự kết nối
   lại vì retry sẵn có trong Micrometer OTLP registry, không cần restart app.
4. Vì alert `ServiceNotReporting` bị inhibit khi alert này đang firing (xem `alertmanager.yml`), khi
   OtelCollectorDown resolve, kiểm tra lại xem 3 service có tự báo cáo trở lại không.

## ServiceNotReporting

**Ý nghĩa**: 1 trong 3 service (label `service=...`) ngưng gửi metrics về collector hơn 3 phút —
nhiều khả năng container đã dừng hoặc mất kết nối mạng. `severity=page`.

**Lưu ý về thời gian phản ứng**: đã đo thật — mất khoảng ~4 phút kể từ lúc service thật sự down đến
khi alert chuyển `firing` (30s để Collector hết hạn giữ metric cũ + 3 phút cửa sổ `absent_over_time`
+ 1 phút `for`). Đừng tưởng hệ thống bị treo nếu chưa thấy alert ngay sau khi service down.

**Bước xử lý đầu tiên**:
1. `docker compose ps <service>` — kiểm tra container có đang chạy không.
2. Nếu container đã dừng: `docker compose start <service>`.
3. Nếu container vẫn chạy nhưng không gửi được metric: kiểm tra network tới `otel-collector:4318`
   (`docker compose logs <service> | grep -i otlp`), khả năng là do collector restart giữa chừng.
4. Sau khi service khởi động lại, kiểm tra `AvailabilitySLOFastBurn`/`AvailabilitySLOSlowBurn` của
   service này (và các service gọi tới nó) có bị kéo theo không.

## JVMHeapUsageHigh

**Ý nghĩa**: heap JVM của service dùng >85% liên tục 10 phút. `severity=ticket` — chỉ để điều tra
nguyên nhân, không tự page (heap cao chưa chắc user bị ảnh hưởng, xem [LatencySLOFastBurn](#latencyslofastburn)
mới là tín hiệu user thật sự bị chậm).

**Bước xử lý đầu tiên**:
1. Mở `dashboard_url` (`jvm-overview`, đã lọc theo service) — xem heap có tăng dần không dừng (nghi
   memory leak) hay chỉ là spike theo tải.
2. Xem GC CPU time cùng lúc — nếu tăng mạnh, GC đang phải chạy liên tục để giải phóng heap.
3. Nếu nghi leak thật, cần heap dump (`jcmd <pid> GC.heap_dump`) để điều tra sâu hơn — việc này ngoài
   phạm vi alert, cần vào tận container.

## ContainerCPUHigh

**Ý nghĩa**: container CPU dùng >80% liên tục 10 phút (đo qua cAdvisor). `severity=ticket`.

**Lưu ý môi trường**: alert này chỉ có data đúng khi cAdvisor liệt kê được từng container — trên
Docker Desktop dùng containerd snapshotter thì sẽ không bao giờ nổ dù CPU thật sự cao (chỉ thấy
cgroup gốc). Xem `observability/README.md` mục cAdvisor để biết cách khắc phục.

**Bước xử lý đầu tiên**:
1. Mở `dashboard_url` (`infra-containers`) xem container nào đang cao.
2. Đối chiếu với [LatencySLOFastBurn](#latencyslofastburn) của cùng service — CPU cao có đang thật
   sự làm chậm request không, hay chỉ là background job (vd GC, compaction) không ảnh hưởng user.
3. Nếu CPU cao kéo dài do tải tăng thật, cân nhắc tăng resource limit hoặc scale thêm instance.
