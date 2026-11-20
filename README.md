# AIDLINK

공식 공공지원 정책을 통합하고, 사용자의 nullable 프로필과 구조화 Rule을 결정론적으로 비교해 자격·부족 조건·상황 변화 결과를 설명하는 모듈형 모놀리스입니다.

AI는 정책 원문을 Rule 후보로 변환할 뿐 자격 여부를 결정하지 않습니다. 최종 판정은 언제나 Java Rule Engine이 수행합니다.

## 구현된 흐름

```text
정부24 공식 API
  → 원본 JSON 불변 저장 + content hash
  → 정규화 + 변경 감지 + Rule 이력 보존
  → AI Structured Output 후보 생성
  → 다중 기준 자동 검증
  → Java Rule Engine(PASS / FAIL / UNKNOWN)
  → Near Miss / What-if Simulation
  → Prometheus + Loki + Tempo 관측
```

- 공개 정책 검색: PostgreSQL FTS, GIN, `pg_trgm`, 지역·분야·기관·신청기간·프로필 조건 필터
- 자격 분석: `AND`/`OR`/`CONDITION`, 3값 논리, 조건별 근거, OR 경로를 고려한 blocker
- 프로필/인증: nullable 프로필, HttpOnly 세션 쿠키, CSRF, BCrypt, 사용자/관리자 권한
- Simulation: 현재 프로필의 불변 복사본에 변경만 적용하고 5개 변화 상태로 비교
- 수집: 정부24 공식 API 계약 검증, canonical hash, Spring Batch chunk/restart/부분 실패
- AI/검수: strict JSON Schema 후보, 자동 Validator, 중복 실행 lock, 관리자 Review Queue/UI
- 캐시/제어: Redis 공개 정책 cache, rate limit, AI 중복 실행 방지
- 관측성: Actuator/Micrometer, Prometheus, Grafana 4종 대시보드, ECS JSON log, Alloy/Loki, OTel/Tempo
- 배포 기반: 멀티 스테이지 Dockerfile, Docker Compose, PostgreSQL/Redis/관측 스택, GitHub Actions

정부24와 OpenAI 연동은 키가 없을 때 fail-closed 상태로 비활성화됩니다. 공식 데이터 수집 전에는 샘플 정책 5개로 검색·판정·Simulation 흐름을 확인할 수 있고, 수집 후에는 실제 정부24 정책이 검색 결과에 먼저 표시됩니다. 샘플의 placeholder URL은 외부 링크로 노출하지 않습니다.

## 빠른 실행

### 전체 스택

```powershell
Copy-Item .env.example .env
# .env의 PostgreSQL/Grafana 비밀번호를 변경
docker compose up --build
```

| 주소 | 용도 |
| --- | --- |
| `http://localhost:3000` | Next.js UI |
| `http://localhost:4173` | 독립 랜딩 페이지(선택 실행) |
| `http://localhost:8080` | Spring Boot API |
| `http://localhost:3002` | Grafana |
| `http://localhost:9091` | Prometheus |
| `http://localhost:3101` | Loki |
| `http://localhost:3200` | Tempo |

PostgreSQL은 기존 로컬 설치와 충돌하지 않도록 호스트 `5433`에서 컨테이너 `5432`로 연결합니다. 관측성 포트는 기존 monitoring stack을 보존하기 위해 Grafana `3002`, Loki `3101`, Prometheus `9091`을 기본값으로 사용하며 `.env`의 `*_HOST_PORT`로 바꿀 수 있습니다.

### 다른 컴퓨터에 임시 공유

Cloudflare 계정이나 도메인 없이 임시 HTTPS 주소를 발급해 현재 컴퓨터의 앱을 공유할 수 있습니다.

```powershell
docker compose -f docker-compose.yml -f docker-compose.share.yml up -d --build cloudflared
docker compose -f docker-compose.yml -f docker-compose.share.yml logs -f cloudflared
```

로그에 출력되는 `https://...trycloudflare.com` 주소로 접속합니다. 종료와 이메일 OTP 제한 방법은 [`docs/local-sharing.md`](docs/local-sharing.md)를 참고하세요.

### 인프라 없이 개발

기본 프로필은 메모리 저장소와 샘플 정책을 사용합니다.

```powershell
cd backend
.\gradlew-safe.ps1 bootRun

cd ..\frontend
npm ci
npm run dev
```

랜딩 페이지만 독립적으로 실행하려면 별도 터미널에서 다음 명령을 사용합니다. 랜딩은 백엔드 없이 실행되며 CTA는 기본적으로 `http://localhost:3000`의 메인 앱으로 연결됩니다.

```powershell
cd landing
npm run dev
```

배포 시 메인 앱 주소는 `AIDLINK_APP_URL`로 지정합니다. 자세한 내용은 [`landing/README.md`](landing/README.md)를 참고하세요.

PostgreSQL 개발 모드는 `SPRING_PROFILES_ACTIVE=postgres`와 `AIDLINK_DB_*` 환경 변수를 사용합니다. 실제 외부 수집/AI 파이프라인 활성화 방법은 [`docs/operations.md`](docs/operations.md)를 참고하세요.

## 검증

```powershell
cd backend
.\gradlew-safe.ps1 cleanTest test --no-daemon

cd ..\frontend
npm run lint
npm run build

cd ..\landing
npm run check
npm run build

cd ..
docker compose config --quiet
```

Windows의 비 ASCII 경로에서 발생하는 Gradle test worker 문제 때문에 `gradlew-safe.ps1`은 실행 중에만 임시 ASCII 드라이브를 연결합니다. CI는 PostgreSQL 17.11 서비스에서 Flyway, JPA, Batch 통합 테스트까지 실행합니다.

## 문서

- [`docs/architecture.md`](docs/architecture.md): 모듈 경계와 런타임 구조
- [`docs/rule-engine.md`](docs/rule-engine.md): 3값 논리, blocker, Near Miss
- [`docs/data-pipeline.md`](docs/data-pipeline.md): 수집·AI·검증·버전 이력
- [`docs/operations.md`](docs/operations.md): 환경 변수, 모니터링, 운영 절차
- [`docs/local-sharing.md`](docs/local-sharing.md): Cloudflare Quick Tunnel 임시 외부 공유
- [`docs/aws-deployment.md`](docs/aws-deployment.md): 2 GiB EC2 + RDS 단일 호스트 배포
- [`docs/technology-versions.md`](docs/technology-versions.md): 안정 버전 선택 기준
- [`docs/api-examples.md`](docs/api-examples.md): API 요청 예시

## 핵심 원칙

1. 미입력 정보는 `FAIL`이 아니라 `UNKNOWN`입니다.
2. AI 응답은 후보이며, 자격 판정의 Source of Truth가 아닙니다.
3. `raw_policy`와 `policy_rule_version`은 변경 이력을 보존합니다.
4. 검수되지 않은 정책도 검색 가능하며 분석 신뢰 수준을 함께 표시합니다.
5. Controller에는 자격 판정과 Simulation 도메인 로직을 두지 않습니다.
6. 현재 해결할 문제가 없는 MQ, OpenSearch, Kubernetes, API Gateway는 포함하지 않습니다.
