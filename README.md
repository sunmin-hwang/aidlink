# AIDLINK 배포 안내서

AIDLINK는 공공지원 정책을 검색하고 사용자 프로필과 정책 조건을 비교해 자격 분석·상황 변화 시뮬레이션을 제공하는 서비스입니다. 이 문서는 **처음 배포하는 사람이 본인 계정으로 독립적인 서비스를 만드는 절차**를 설명합니다.

기본 배포는 **Neon(PostgreSQL 17) → Render(Spring Boot API) → Vercel(Next.js 웹)** 순서로 진행합니다. 샘플 정책으로 먼저 동작을 확인한 뒤 정부24 실제 데이터를 수집할 수 있습니다. 기본 배포에는 OpenAI 결제나 로컬 개발 도구 설치가 필요하지 않습니다.

> 작성 기준: 2026-11-20. 각 서비스의 화면 이름과 무료 제공량은 변경될 수 있으므로 가입·생성 화면에서 Free/Hobby 여부를 확인해 주세요. 이 안내는 수업 시연과 테스트를 위한 구성입니다.

## 목차

1. [배포 구조와 준비물](#1-배포-구조와-준비물)
2. [GitHub 저장소 준비](#2-github-저장소-준비)
3. [Neon 데이터베이스 생성](#3-neon-데이터베이스-생성)
4. [Render 백엔드 배포](#4-render-백엔드-배포)
5. [Vercel 프론트엔드 배포](#5-vercel-프론트엔드-배포)
6. [백엔드에 프론트엔드 주소 등록](#6-백엔드에-프론트엔드-주소-등록)
7. [서비스 동작 확인](#7-서비스-동작-확인)
8. [정부24 정책 최초 수집 — 선택](#8-정부24-정책-최초-수집--선택)
9. [GitHub Actions로 서버 깨우기 — 선택](#9-github-actions로-서버-깨우기--선택)
10. [관리자와 AI 기능 — 선택](#10-관리자와-ai-기능--선택)
11. [수정 사항 재배포](#11-수정-사항-재배포)
12. [문제 해결](#12-문제-해결)
13. [배포 종료와 리소스 정리](#13-배포-종료와-리소스-정리)
14. [로컬 실행 — 선택](#14-로컬-실행--선택)

## 1. 배포 구조와 준비물

```text
사용자 브라우저
    │ HTTPS
    ▼
Vercel: frontend/ (Next.js)
    │ /api/* 요청을 Render로 전달
    ▼
Render: backend/ (Spring Boot, Java 21, Docker)
    │ JDBC + SSL
    ▼
Neon: PostgreSQL 17
```

| 준비물 | 용도 |
| --- | --- |
| [GitHub 계정](https://github.com/) | 소스 코드 저장 및 배포 서비스 연결 |
| [Neon 계정](https://console.neon.tech/) | PostgreSQL 데이터베이스 |
| [Render 계정](https://dashboard.render.com/) | Java 백엔드 실행 |
| [Vercel 계정](https://vercel.com/) | 웹 프론트엔드 실행 |
| 공공데이터포털 계정·API 인증키(선택) | 정부24 실제 정책 수집 |

각 서비스에서 GitHub 로그인을 사용하면 연결이 편리합니다. 이메일 본인 확인이나 GitHub 저장소 접근 승인 화면이 나오면 직접 완료합니다.

- 프론트엔드와 백엔드는 **동일한 GitHub 저장소**를 사용합니다. 서비스마다 Root Directory만 다르게 지정합니다.
- 별도 도메인은 필요하지 않습니다. Render와 Vercel이 HTTPS 주소를 발급합니다.
- Redis, Grafana, Prometheus, Loki, Tempo는 이번 무료 배포에서 실행하지 않습니다.
- Vercel은 **Hobby**, Render와 Neon은 **Free**를 선택합니다. Vercel Hobby는 개인의 비상업적 사용을 위한 플랜입니다. [Hobby 공식 안내](https://vercel.com/docs/plans/hobby)
- Render Free는 요청이 약 15분간 없으면 절전 상태로 전환됩니다. 다시 요청하면 깨어나지만 이 프로젝트의 실제 테스트에서는 Java 기동에 약 3분이 걸렸습니다. 월별 실행 시간 등 무료 한도도 적용됩니다. [Render 무료 제한](https://render.com/docs/free)

배포하면서 다음 세 가지를 기록해 두세요. 아래의 `YOUR_...`는 설명용 자리표시자이므로 실제 값으로 바꿉니다.

| 기록할 값 | 예시 |
| --- | --- |
| Neon 직접 연결 호스트 | `ep-YOUR_ENDPOINT.ap-southeast-1.aws.neon.tech` |
| Render 백엔드 주소 | `https://YOUR_BACKEND.onrender.com` |
| Vercel 프론트엔드 주소 | `https://YOUR_FRONTEND.vercel.app` |

DB 비밀번호와 API 인증키는 각 서비스의 환경 변수 화면에서만 입력합니다. README, GitHub 코드, 이슈에 올리지 않습니다.

## 2. GitHub 저장소 준비

1. 원본 저장소 [sunmin-hwang/aidlink](https://github.com/sunmin-hwang/aidlink)에 접속합니다.
2. 오른쪽 위 **Fork**를 눌러 본인 계정에 복사합니다. 기본 브랜치 `main`이 포함되도록 합니다.
3. 본인 계정의 저장소에 `backend/`, `frontend/`, `.github/workflows/`가 있는지 확인합니다.
4. 이후 Render와 Vercel에서는 **본인 계정으로 복사한 저장소**를 선택합니다.
5. GitHub 앱 접근 권한을 요청하면 해당 저장소 접근을 허용합니다. 목록에 보이지 않으면 설치된 GitHub 앱의 저장소 접근 설정을 확인합니다.

`docs/`와 저장소 루트의 `landing/`은 Git 추적 대상에서 제외되어 있으므로 배포에 필요하지 않습니다. 실제 서비스의 랜딩 페이지는 `frontend/public/landing/`에 포함되어 있습니다. 이 안내서는 별도 `docs/` 문서 없이 배포할 수 있도록 작성했습니다.

## 3. Neon 데이터베이스 생성

### 3-1. 프로젝트 생성

1. Neon에 로그인하고 **New Project / Create Project**를 선택합니다.
2. 프로젝트 이름을 입력합니다. 예: `aidlink-demo`.
3. **PostgreSQL 버전은 17**을 선택합니다. 이 프로젝트가 배포·검증한 버전입니다.
4. 클라우드는 **AWS**, 리전은 **Singapore (`ap-southeast-1`)**를 선택합니다. Render 백엔드도 Singapore로 맞춥니다.
5. **Free** 플랜으로 생성합니다.
6. Databases 메뉴에서 `aidlink` 데이터베이스를 생성합니다. 기본으로 제공되는 `neondb`를 사용해도 되지만 이후 JDBC URL의 DB 이름도 반드시 `neondb`로 맞춰야 합니다.

PostgreSQL 18 등 다른 버전으로 이미 만들었다면 버전 17의 새 프로젝트를 생성해 사용합니다. 기존 데이터베이스에 테이블을 직접 만들 필요는 없습니다. 첫 백엔드 실행 시 Flyway가 스키마와 샘플 정책을 생성합니다.

### 3-2. 접속 정보 확인

프로젝트의 **Connect** 화면에서 사용할 브랜치(예: `production`), 데이터베이스(`aidlink`), Role을 선택합니다. **Connection pooling을 끄고 직접 연결(Direct connection)** 정보를 확인합니다. 이번 구성은 애플리케이션의 HikariCP를 사용합니다.

다음 값을 기록합니다.

| 항목 | 사용할 위치 |
| --- | --- |
| Host | `AIDLINK_DB_URL`의 호스트 부분 |
| Database | `AIDLINK_DB_URL`의 마지막 경로 |
| Role / User | `AIDLINK_DB_USERNAME` |
| Password | `AIDLINK_DB_PASSWORD` |

Neon의 일반 접속 문자열은 다음 형태입니다.

```text
postgresql://ROLE:PASSWORD@HOST/aidlink?sslmode=require
```

**이 문자열 전체를 백엔드 DB URL에 붙여넣지 않습니다.** Java용 JDBC URL과 사용자명·비밀번호를 분리합니다.

```text
AIDLINK_DB_URL=jdbc:postgresql://HOST:5432/aidlink?sslmode=require
AIDLINK_DB_USERNAME=ROLE
AIDLINK_DB_PASSWORD=Neon에서 확인한 실제 비밀번호
```

`HOST`, `ROLE`은 실제 값으로 바꿉니다. 비밀번호는 URI에서 복사한 인코딩된 문자열 대신 Connect 화면의 Password 값을 사용합니다. JDBC URL에는 비밀번호를 넣지 않습니다. [Neon 연결 안내](https://neon.com/docs/connect/connect-from-any-app)

## 4. Render 백엔드 배포

### 4-1. Web Service 생성

1. Render에 로그인합니다.
2. **New → Web Service**를 선택합니다. Static Site나 Background Worker가 아닙니다.
3. GitHub를 연결하고 2단계에서 복사한 저장소를 선택합니다.
4. 다음과 같이 입력합니다.

| 설정 | 값 |
| --- | --- |
| Name | 원하는 고유 이름, 예: `aidlink-demo-backend` |
| Branch | `main` |
| Region | Singapore |
| Language / Runtime | **Docker** |
| Root Directory | `backend` |
| Dockerfile Path | `./Dockerfile` |
| Docker Build Context Directory | `.` |
| Instance Type | **Free** |
| Health Check Path | `/actuator/health/liveness` |
| Docker Command | **비워 둠** |

경로는 Root Directory인 `backend` 기준입니다. 따라서 Dockerfile Path에 `backend/Dockerfile`을 중복 입력하지 않습니다. Dockerfile이 Java 21 설치와 JAR 빌드·실행을 처리하므로 Build Command나 Start Command를 따로 작성하지 않습니다. [Render Docker 안내](https://render.com/docs/docker)

### 4-2. 환경 변수 입력

생성 화면의 Environment Variables 또는 생성 후 **Environment** 메뉴에 아래 값을 추가합니다. 입력할 때 이름과 값에 따옴표를 붙이지 않습니다.

| 이름 | 값 | 설명 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `postgres` | Neon 영구 저장소 사용 |
| `AIDLINK_DB_URL` | `jdbc:postgresql://HOST:5432/aidlink?sslmode=require` | 3단계에서 만든 실제 JDBC URL |
| `AIDLINK_DB_USERNAME` | Neon Role 이름 | 실제 사용자명 |
| `AIDLINK_DB_PASSWORD` | Neon Password | 실제 비밀번호 |
| `SERVER_PORT` | `10000` | Render 수신 포트 |
| `SERVER_FORWARD_HEADERS_STRATEGY` | `framework` | HTTPS 프록시 헤더 처리 |
| `JAVA_TOOL_OPTIONS` | `-Xms64m -Xmx256m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC` | 무료 인스턴스 메모리 설정 |
| `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` | `3` | DB 연결 수 제한 |
| `SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE` | `0` | 유휴 연결 최소화 |
| `AIDLINK_ENVIRONMENT` | `render-demo` | 로그 환경 이름 |
| `AIDLINK_SECURE_COOKIE` | `true` | HTTPS 세션 쿠키 |
| `AIDLINK_REDIS_ENABLED` | `false` | Redis 없이 실행 |
| `AIDLINK_OTLP_TRACING_ENABLED` | `false` | 외부 추적 수집기 없이 실행 |
| `AIDLINK_OPENAI_ENABLED` | `false` | 기본 배포에서는 AI 외부 호출 끔 |
| `AIDLINK_GOV24_SCHEDULING_ENABLED` | `false` | 자동 수집 끔 |
| `AIDLINK_AI_PIPELINE_SCHEDULING_ENABLED` | `false` | AI 자동 처리 끔 |
| `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` | `health,info` | 상태 확인 API만 공개 |
| `MANAGEMENT_HEALTH_REDIS_ENABLED` | `false` | 미사용 Redis 상태 검사 끔 |

`AIDLINK_CORS_ALLOWED_ORIGINS`는 Vercel 주소를 받은 뒤 6단계에서 추가합니다. 정부24 API 키도 실제 수집을 진행할 때 추가하면 됩니다.

### 4-3. 최초 실행 확인

1. **Deploy Web Service**를 누릅니다.
2. Logs에서 Docker 빌드와 Spring Boot 시작 로그를 확인합니다. 최초 빌드와 무료 인스턴스 기동에는 수 분 이상 걸릴 수 있습니다.
3. `Started AidLinkApplication`과 서비스가 Live 상태가 되는 것을 확인합니다. 로그의 애플리케이션 클래스 표기는 버전에 따라 다를 수 있습니다.
4. Render가 발급한 `https://...onrender.com` 주소를 기록합니다.
5. 브라우저에서 아래 주소를 엽니다.

```text
https://YOUR_BACKEND.onrender.com/actuator/health/liveness
```

정상 응답:

```json
{"status":"UP"}
```

다음 주소도 열어 봅니다.

```text
https://YOUR_BACKEND.onrender.com/api/v1/policies/search?page=0&size=5
```

새 DB라면 샘플 정책이 조회되고 `totalElements`가 5로 표시됩니다. 이미 데이터를 수집한 DB라면 다른 수가 나옵니다. Render 기본 주소 `/`에서 404가 나오더라도 위 API가 정상이라면 백엔드는 동작 중입니다.

## 5. Vercel 프론트엔드 배포

### 5-1. 프로젝트 가져오기

1. Vercel에 로그인하고 개인 계정의 **Hobby** 플랜을 사용합니다.
2. **Add New → Project**를 선택합니다.
3. GitHub에서 본인 계정으로 복사한 저장소를 Import합니다.
4. 다음과 같이 입력합니다.

| 설정 | 값 |
| --- | --- |
| Project Name | 원하는 고유 이름, 예: `aidlink-demo-frontend` |
| Framework Preset | **Next.js** |
| Root Directory | **`frontend`** |
| Node.js Version | **24.x** |
| Install Command | 기본값 또는 `npm ci` |
| Build Command | 기본값 또는 `npm run build` |
| Output Directory | 프레임워크 기본값 유지 |
| Production Branch | `main` |

Node.js 버전 항목이 생성 화면에 없다면 프로젝트의 **Settings → Build and Deployment**에서 설정한 뒤 재배포합니다. 저장소의 `frontend/package.json`도 Node 24를 요구합니다.

### 5-2. 환경 변수 설정

| 이름 | 값 |
| --- | --- |
| `BACKEND_API_BASE_URL` | `https://YOUR_BACKEND.onrender.com` |

Render에서 발급받은 실제 HTTPS 주소로 바꿉니다. 뒤에 `/api`를 붙이지 않습니다. **Production** 환경에 적용하고, Preview에서도 동작을 확인할 계획이면 Preview에도 적용합니다.

**`NEXT_PUBLIC_API_BASE_URL`은 추가하지 않습니다.** 이미 추가했다면 삭제하거나 빈 값으로 둡니다. 프론트엔드는 `/api/*` 요청을 같은 Vercel 도메인으로 보내고 Next.js가 Render로 전달하도록 구현되어 있습니다. 이 방식으로 로그인 세션과 CSRF 쿠키를 처리합니다.

1. **Deploy**를 누릅니다.
2. 이메일 본인 확인이 나오면 Vercel 화면에서 직접 완료합니다.
3. 배포 상태가 **Ready**가 되면 프로젝트의 Production 도메인 `https://...vercel.app`을 기록합니다.
4. 루트 주소에서 랜딩 페이지가 표시되는지 확인합니다.

환경 변수를 배포 후 수정했다면 **Deployments → 해당 배포 → Redeploy**를 실행합니다. 특히 백엔드 주소는 Next.js 빌드 시 rewrite에도 사용되므로 새 빌드가 필요합니다. [Vercel 환경 변수 안내](https://vercel.com/docs/environment-variables)

## 6. 백엔드에 프론트엔드 주소 등록

1. Render 백엔드의 **Environment** 메뉴를 엽니다.
2. 다음 변수를 추가합니다.

```text
AIDLINK_CORS_ALLOWED_ORIGINS=https://YOUR_FRONTEND.vercel.app
```

3. 프로젝트의 실제 Production 도메인을 사용합니다. 경로와 마지막 `/`는 붙이지 않습니다.
4. **Save, rebuild, and deploy** 등 변경 사항을 저장하고 배포하는 옵션을 선택합니다.
5. 다시 Live가 되고 liveness가 `UP`인지 확인합니다.

여러 프론트엔드 주소를 명시해야 한다면 쉼표로 구분합니다. 처음 테스트는 고정된 Production 도메인 한 곳에서 진행하는 편이 단순합니다.

## 7. 서비스 동작 확인

다음 순서대로 확인합니다. Render가 잠들어 있었다면 먼저 백엔드 liveness 주소를 열어 기동을 기다린 뒤 웹 화면을 새로고침합니다. 프론트 요청이 먼저 시간 초과되어도 서버 기동 후 다시 시도할 수 있습니다.

| 확인 항목 | 기대 결과 |
| --- | --- |
| Render `/actuator/health/liveness` | `{"status":"UP"}` |
| Vercel `/` | 랜딩 페이지 표시 |
| Vercel `/api/v1/policies/search?page=0&size=5` | 정책 JSON 응답, 새 DB 기준 샘플 5개 |
| 웹의 정책 목록·상세 | 정책 이름과 설명 표시 |
| 회원가입 | 직접 입력한 이메일·8자 이상의 비밀번호로 가입 가능 |
| 로그인 | 가입한 계정으로 로그인 가능 |
| 내 프로필 | 입력·저장 후 다시 조회 가능 |
| 자격 분석·시뮬레이션 | 프로필과 샘플 정책 조건의 비교 결과 표시 |
| 로그아웃 | 인증이 필요한 화면에서 로그인 필요 상태로 변경 |

**새 데이터베이스에는 기존 시연 계정이 복제되지 않습니다.** 배포한 화면에서 직접 테스트 계정을 만들어 사용합니다. 실제 운영 계정의 비밀번호를 문서에 기록하지 않습니다.

입력하지 않은 프로필 항목이나 구조화되지 않은 정책 조건은 `UNKNOWN`으로 표시될 수 있습니다. 이는 정보 부족을 나타내는 정상적인 판정입니다. 공식 데이터를 수집하기 전 샘플 정책으로 분석을 먼저 확인하면 됩니다.

## 8. 정부24 정책 최초 수집 — 선택

샘플 데이터로 배포 확인만 할 경우 이 단계는 생략할 수 있습니다. 실제 정책 검색을 재현하려면 아래 절차를 진행합니다. **정책 수집과 AI 조건 분석은 별도 작업**이며 수집에는 OpenAI API 키가 필요하지 않습니다.

### 8-1. 공공데이터포털 인증키 준비

1. [행정안전부_대한민국 공공서비스(혜택) 정보](https://www.data.go.kr/data/15113968/openapi.do)에 접속합니다.
2. 공공데이터포털에 로그인하고 해당 OpenAPI를 활용 신청합니다.
3. 승인 상태와 인증키를 확인합니다. 이 프로젝트는 `/gov24/v3/serviceList`, `/gov24/v3/serviceDetail`을 사용합니다.
4. Render 환경 변수에 `AIDLINK_GOV24_SERVICE_KEY`를 추가하고 발급받은 키를 입력합니다. 일반 인증키의 Decoding 값을 사용하며 앞뒤 공백·따옴표를 넣지 않습니다.
5. 자동 수집과 AI 자동 처리 변수는 기존의 `false`를 유지합니다.

### 8-2. 수집을 한 번 실행

무료 Render의 별도 작업 기능에 의존하지 않고, **백엔드 시작 명령을 잠시 변경**해 Spring Batch를 한 번 실행합니다. 이 작업은 DB에 정책을 저장합니다. DB를 초기화하거나 지울 필요가 없습니다.

1. Render의 **Settings → Docker Command**를 엽니다.
2. 아래 명령을 한 줄로 입력합니다.

```text
java -jar /app/app.jar --spring.batch.job.enabled=true --spring.batch.job.name=gov24PolicySyncJob pageSize=500 importedAt=initial-20261120-001
```

3. `importedAt`은 작업을 구분하는 값입니다. 별도 수집을 새로 시작할 때는 `initial-20261121-001`처럼 새로운 값을 사용합니다. 동일한 작업을 여러 인스턴스에서 동시에 실행하지 않습니다.
4. 저장 후 배포하고 Logs를 확인합니다. 정부 API와 DB 상태에 따라 시간이 걸리므로 **Live 표시만 보고 수집이 끝났다고 판단하지 않습니다.**
5. `gov24PolicySyncJob`의 종료 상태가 **`COMPLETED`**인지 확인합니다. 실패했다면 아래 복구 절차에 따라 먼저 일반 실행 설정으로 돌아갑니다.

Neon SQL Editor에서 다음 조회로도 확인할 수 있습니다.

```sql
SELECT job_execution_id, status, start_time, end_time, exit_code
FROM batch_job_execution
ORDER BY job_execution_id DESC
LIMIT 5;

SELECT COUNT(*) AS government_policy_count
FROM policy
WHERE external_id LIKE 'GOV24:%';
```

기존 배포에서는 10,891개를 수집했지만 실제 수는 정부 API의 현재 데이터에 따라 달라집니다. 검색 결과의 `totalElements`와 실제 정책 이름도 확인합니다.

### 8-3. 반드시 일반 실행으로 복구

**수집 완료 후 또는 실패해 작업을 중단할 때 반드시 다음을 진행합니다.**

1. Render **Settings → Docker Command**의 임시 명령을 **완전히 지웁니다**.
2. 저장 후 다시 배포합니다. Dockerfile의 기본 실행 명령으로 돌아갑니다.
3. Live와 liveness `UP`를 확인합니다.
4. 새 시작 로그에서 `gov24PolicySyncJob`이 다시 자동 실행되지 않는지 확인합니다.
5. Vercel에서 실제 정책 검색·상세 화면을 확인합니다.

수집한 데이터는 Neon에 있으므로 재배포해도 유지됩니다. API 키는 Render 환경 변수에 보관할 수 있습니다. 최초 수집 후 자동 갱신을 원하지 않으면 `AIDLINK_GOV24_SCHEDULING_ENABLED=false`를 유지합니다.

수집된 정책은 원문 저장·검색이 가능하지만 AI 분석과 검수를 마치지 않은 정책은 `RAW` 또는 `UNKNOWN` 상태일 수 있습니다. 수집 성공이 모든 정책의 자격 조건 검증 완료를 의미하지는 않습니다.

## 9. GitHub Actions로 서버 깨우기 — 선택

저장소에는 `.github/workflows/render-wake.yml`이 포함되어 있습니다. 이 워크플로는 Render liveness 주소에 요청을 보내 **서버를 깨우기만 합니다. 정책 수집을 실행하지 않습니다.**

1. 복사한 GitHub 저장소의 **Actions** 탭에서 워크플로 사용을 활성화합니다. Fork에서는 예약 실행이 기본적으로 비활성화될 수 있습니다.
2. **Settings → Secrets and variables → Actions → Variables → New repository variable**을 엽니다. Secret이 아닌 Repository variable입니다.
3. 다음 값을 등록합니다.

```text
Name: RENDER_BACKEND_URL
Value: https://YOUR_BACKEND.onrender.com
```

4. **Actions → Wake Render backend → Run workflow → main → Run workflow**로 수동 실행합니다.
5. 실행 결과가 성공하고 로그에 `Backend is awake.`가 출력되는지 확인합니다.

현재 예약은 매일 **한국 시간 02:55**, GitHub cron 표현식은 UTC 기준 `55 17 * * *`입니다. 예약 워크플로는 기본 브랜치의 파일을 사용합니다. GitHub 사정에 따라 실행이 늦어지거나 비활성화될 수 있으므로 정확한 시각을 보장하지 않습니다. [GitHub 예약 이벤트 안내](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#schedule)

자동 정책 수집을 추가로 원할 때만 Render의 다음 값을 설정합니다.

```text
AIDLINK_GOV24_SCHEDULING_ENABLED=true
AIDLINK_GOV24_CRON=0 0 3 * * *
```

위 Spring cron은 한국 시간 03:00입니다. 정부24 키가 필요하며 백엔드가 그 시각에 실행 중이어야 합니다. 무료 서버가 잠들어 있던 동안의 스케줄은 자동 보충되지 않고, 깨우기 요청도 정확한 정시 실행을 보장하지 않습니다. **수업 시연은 8단계의 수동 최초 수집만으로 충분합니다.**

## 10. 관리자와 AI 기능 — 선택

기본 배포는 OpenAI를 비활성화하고 샘플 정책의 구조화 조건으로 분석을 확인하는 구성입니다. AI는 정책 원문에서 조건 후보를 추출하며 최종 자격 판정은 Java Rule Engine이 수행합니다.

### 관리자 계정 최초 생성

관리자 화면을 확인할 때만 Render 환경 변수에 다음을 잠시 추가하고 배포합니다.

| 이름 | 값 |
| --- | --- |
| `AIDLINK_BOOTSTRAP_ADMIN_ENABLED` | `true` |
| `AIDLINK_BOOTSTRAP_ADMIN_EMAIL` | 본인이 정한 관리자 이메일 |
| `AIDLINK_BOOTSTRAP_ADMIN_PASSWORD` | 본인이 정한 12자 이상의 비밀번호 |

로그인 후 관리자 화면에 접근되는지 확인합니다. 생성이 완료되면 `AIDLINK_BOOTSTRAP_ADMIN_ENABLED=false`로 바꾸고 비밀번호 환경 변수를 제거한 뒤 재배포합니다. 생성된 계정은 DB에 유지됩니다. 기존 일반 사용자와 구별되는 새 이메일로 생성하는 것을 권장합니다.

### AI 조건 추출

AI 연동을 테스트하려면 유효한 OpenAI API 계정·키와 별도의 API 사용료가 필요합니다. 무료 배포 검증의 필수 항목이 아닙니다.

| 이름 | 값 |
| --- | --- |
| `AIDLINK_OPENAI_ENABLED` | `true` |
| `AIDLINK_OPENAI_API_KEY` | 본인이 발급한 API 키 |
| `AIDLINK_OPENAI_MODEL` | 해당 계정에서 사용 가능하며 Structured Outputs를 지원하는 모델 ID |
| `AIDLINK_AI_PIPELINE_SCHEDULING_ENABLED` | `false` 유지 |

재배포 후 관리자 화면에서 소수의 정책만 선택해 조건 추출·검증 흐름을 확인합니다. 모델 ID는 본인 계정의 실제 사용 가능 목록을 확인해 지정합니다. 전체 정책 자동 처리는 사용량과 비용을 확인한 후 별도로 설정합니다.

## 11. 수정 사항 재배포

두 서비스의 Production Branch를 `main`으로 유지합니다. Render 자동 배포가 켜져 있고 Vercel Git 연동이 되어 있다면 **`main`에 push하면 새 배포가 시작됩니다.** 단순 문서 수정도 서비스 설정에 따라 빌드를 유발할 수 있습니다.

권장 흐름:

```text
dev에서 수정 → 로컬 확인 → GitHub에 dev push → 검토 → main에 merge → 배포 확인
```

Fork에 `dev` 브랜치가 없다면 GitHub 브랜치 메뉴에서 `main`을 기준으로 생성합니다. 로컬 Git을 사용하는 경우:

```bash
git clone https://github.com/YOUR_GITHUB_ID/aidlink.git
cd aidlink
git switch -c dev
# 파일 수정 및 필요한 확인
git add README.md
git commit -m "docs: update deployment guide"
git push -u origin dev
```

실제 수정 파일에 맞게 `git add` 대상을 바꾸고 GitHub에서 **base: main ← compare: dev**인 Pull Request를 생성합니다. CI 결과를 확인한 뒤 merge하고 Render Live와 Vercel Ready를 확인합니다. 이미 `dev`가 있다면 새로 생성하지 않고 `git switch dev`로 이동합니다.

Vercel은 `dev` push에도 Preview를 만들 수 있습니다. Preview의 환경 변수에 운영 백엔드를 지정했다면 **운영 DB를 사용하는 테스트**가 됩니다. 운영 데이터와 분리할 필요가 있으면 별도 Neon DB와 Render 서비스가 필요합니다.

## 12. 문제 해결

| 증상 | 확인·해결 방법 |
| --- | --- |
| 저장소가 Render/Vercel 목록에 없음 | GitHub 앱의 저장소 접근 권한과 연결 계정을 확인합니다. |
| Render 빌드에서 파일을 찾지 못함 | Root Directory=`backend`, Dockerfile=`./Dockerfile`, Context=`.`인지 확인합니다. |
| Render에서 메모리 부족·프로세스 종료 | `JAVA_TOOL_OPTIONS`를 확인하고 기본 배포에서 Redis·AI·관측 수집기가 비활성화되어 있는지 확인합니다. |
| DB 접속 실패 | PostgreSQL 17 프로젝트의 Host·DB·Role·Password가 서로 같은 연결 정보인지 확인합니다. JDBC 접두어와 `sslmode=require`도 확인합니다. |
| Flyway 시작 실패 | Logs의 최초 DB 오류를 확인합니다. 기존 마이그레이션 SQL을 임의 수정하거나 `flyway_schema_history`를 삭제하지 않습니다. |
| 사이트는 열리지만 API가 502/504 또는 시간 초과 | Render liveness를 직접 열고 Java 기동을 기다린 뒤 다시 시도합니다. 계속 실패하면 Render Logs와 Vercel의 백엔드 URL을 확인합니다. |
| API 요청이 localhost로 향함 | Vercel의 `BACKEND_API_BASE_URL`을 실제 Render 주소로 설정하고 다시 빌드·배포합니다. |
| 로그인 직후 다시 로그인 화면으로 이동 | Vercel Production 도메인으로 접속했는지, 공개 API URL 변수가 비어 있는지, Render의 Secure Cookie·전달 헤더·CORS 값이 맞는지 확인합니다. 백엔드 재시작 시 세션이 만료되어 재로그인이 필요할 수 있습니다. |
| 프로필 저장·로그아웃에서 403 | 웹에서 다시 로그인하고 재시도합니다. 수동 API 호출은 세션 쿠키와 CSRF 토큰이 모두 필요합니다. |
| 샘플 정책만 표시됨 | 정부24 수집은 기본적으로 실행되지 않습니다. 8단계를 진행합니다. |
| 정부 API 401/403 | 해당 API 활용 신청 승인과 인증키, 앞뒤 공백, Decoding 키 사용 여부를 확인합니다. |
| 정부 API 호출량 초과 | 공공데이터포털 이용 한도와 오류 응답을 확인하고 중복 실행을 중단합니다. |
| 수집이 FAILED | Logs와 `batch_job_execution`을 확인합니다. 임시 Docker Command를 지워 일반 실행으로 복구하고 원인을 해결한 뒤 다시 실행합니다. |
| 정책은 보이는데 분석이 UNKNOWN | 프로필 누락 또는 정책 조건 미구조화 상태일 수 있습니다. 샘플 정책으로 기본 분석을 확인하고 실제 정책은 별도 조건 추출·검수가 필요합니다. |
| GitHub 깨우기 작업 실패 | Repository variable 이름과 주소, Actions 활성화 여부를 확인합니다. 백엔드 기동이 오래 걸리면 Live 확인 후 다시 실행합니다. |

## 13. 배포 종료와 리소스 정리

테스트 종료 시 보관할 데이터가 있다면 먼저 DB를 백업합니다. 이후 Vercel 프로젝트와 Render 서비스를 삭제하고 Neon 프로젝트를 삭제하면 됩니다. Neon 프로젝트 삭제는 저장된 사용자·프로필·정책 데이터를 제거합니다.

GitHub Actions 예약을 더 이상 사용하지 않으면 **Wake Render backend → Disable workflow**로 비활성화합니다. GitHub 저장소를 삭제하거나 변경하는 것만으로 외부 서비스의 모든 리소스가 함께 삭제되지는 않습니다.

## 14. 로컬 실행 — 선택

클라우드 배포에는 필요 없지만 수정 사항을 확인하려면 **JDK 21, Node.js 24, Git**을 설치합니다. 기본 로컬 실행은 메모리 저장소와 샘플 정책을 사용해 PostgreSQL 없이 동작합니다.

Windows PowerShell의 첫 번째 터미널:

```powershell
cd backend
.\gradlew-safe.ps1 bootRun
```

두 번째 터미널:

```powershell
cd frontend
Copy-Item .env.example .env.local
npm ci
npm run dev
```

프로젝트 루트에서 각각 실행하며 브라우저는 `http://localhost:3000`으로 접속합니다. 프론트엔드 예제 환경 변수는 백엔드 `http://localhost:8080`과 같은 도메인 API 프록시를 사용하도록 되어 있습니다. 메모리 모드의 사용자와 변경 데이터는 백엔드 재시작 시 유지되지 않습니다.

macOS/Linux에서는 백엔드 디렉터리에서 `chmod +x gradlew` 후 `./gradlew bootRun`을 사용하고, 프론트엔드 예제 파일은 `cp .env.example .env.local`로 복사합니다.

코드 수정 후 확인 명령은 다음과 같습니다. 단순 README 수정에는 전체 애플리케이션 테스트가 필요하지 않습니다.

```powershell
# backend 디렉터리
.\gradlew-safe.ps1 cleanTest test --no-daemon

# frontend 디렉터리
npm run lint
npm run build
```

## 배포 완료 체크리스트

- [ ] 본인 GitHub 저장소를 Render와 Vercel에 연결했다.
- [ ] Neon PostgreSQL 17 접속 정보를 Render에 입력했다.
- [ ] Render Live 및 liveness `UP`를 확인했다.
- [ ] Vercel Ready 및 랜딩·정책 목록을 확인했다.
- [ ] Vercel API 프록시를 통해 정책 JSON이 조회된다.
- [ ] 직접 만든 계정으로 로그인·프로필 저장·분석을 확인했다.
- [ ] 실제 데이터를 수집했다면 Batch `COMPLETED`와 정책 건수를 확인했다.
- [ ] 수집용 임시 Docker Command를 지우고 일반 실행으로 복구했다.
- [ ] 비밀번호와 API 키를 GitHub에 올리지 않았다.
- [ ] 두 서비스의 Production Branch가 `main`이다.
