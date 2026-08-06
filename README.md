# monitoring-demo

Học Prometheus + Grafana (+ OTel Collector, Loki, Tempo, Alertmanager) theo kiến trúc production
hiện đại, qua 3 microservice Spring Boot 4.1 / Java 26: `user-service`, `product-service`,
`order-service`.

## Cấu trúc project

```
monitoring-demo/
├── common/           # thư viện dùng chung: exception handling, correlation-id, quy ước logging
├── user-service/     # quản lý user
├── product-service/  # quản lý product (tên, giá, tồn kho)
├── order-service/    # tạo order — gọi user-service + product-service qua RestClient
├── observability/    # toàn bộ config Prometheus/Grafana/Alertmanager/OTel Collector/Loki/Tempo
├── docker-compose.yml
└── verify.md         # checklist tự tay verify từng phần của observability stack
```

## Chạy thử

```bash
docker compose up -d --build
```

3 service: user-service `:8090`, product-service `:8091`, order-service `:8092` (đều tiền tố
`/api`, vd `POST /api/orders`).

## Observability stack

Toàn bộ chi tiết kiến trúc (Prometheus/Grafana/Alertmanager/OTel Collector/Loki/Tempo), URL từng
thành phần, giải thích SLO/burn-rate, và các gotcha thật đã gặp khi triển khai: xem
[`observability/README.md`](observability/README.md).

Checklist tự verify từng bước (metrics, SLO, dashboard, alerting, logs, traces): xem
[`verify.md`](verify.md).
