# AIDLINK AWS 배포 안내서

AIDLINK는 공공지원 정책을 검색하고 사용자 프로필과 정책 조건을 비교하는 서비스입니다. 이 문서는 **본인의 AWS·GitHub 계정으로 서비스를 새로 배포하는 과정**을 설명합니다. 명령어의 `YOUR_...`는 실제 값으로 바꿉니다.

현재 운영 주소는 **https://aidlink.duckdns.org** 입니다. 아래 절차로 별도 서비스를 만들 때는 본인 도메인과 새 DB를 사용합니다. 기존 운영 계정의 Terraform 상태·환경 변수·DB를 재사용하지 않습니다.

작성 기준: 2026-11-25. 실제 배포 파일은 `infra/aws/terraform/`, `docker-compose.aws-lean.yml`, `.github/workflows/deploy-aws.yml`입니다. 현재 구성은 AWS 크레딧을 사용할 수 있지만 **항상 무료인 구성은 아닙니다.**

## 1. 배포 구조

```text
사용자 브라우저
    │ HTTPS :443
    ▼
DuckDNS 또는 본인 도메인 → EC2 Elastic IP
    │
    └─ EC2 t4g.small · Ubuntu 24.04 · ARM64 · RAM 2GiB
         ├─ Caddy: HTTPS 인증서 발급 및 요청 분배
         ├─ /api/* → Spring Boot · Java 21 · :8080
         └─ 나머지 → Next.js · Node.js 24 · :3000
                         │ 서버 측 API 요청
                         └────────────────→ Spring Boot
                                                │ JDBC + TLS :5432
                                                ▼
                                  RDS PostgreSQL 17 · db.t4g.micro
                                  외부 직접 접속 비활성화

GitHub dev → 검증 → main → CI 성공
    → GitHub ARM runner에서 이미지 병렬 빌드
    → GHCR에 이미지 저장
    → EC2 self-hosted runner에서 이미지 다운로드 및 교체

Terraform 상태 → 별도 비공개 S3 버킷
```

| 구성 | 이 저장소의 기본값 |
| --- | --- |
| 리전 / EC2·RDS 가용 영역 | 서울 `ap-northeast-2` / `ap-northeast-2a` |
| EC2 | `t4g.small`, ARM64, gp3 20GB, swap 2GB |
| DB | RDS PostgreSQL 17, `db.t4g.micro`, Single-AZ, gp3 20GB |
| 네트워크 | 기본 VPC·기본 서브넷 사용, EC2와 RDS는 같은 AZ |
| 외부 공개 포트 | 웹 TCP 80/443, HTTP/3 UDP 443, 관리자 IP에만 SSH 22 |
| DB 접근 | EC2 애플리케이션 보안 그룹에서만 5432 허용 |
| 백업 | RDS 자동 백업 7일, 삭제 보호, 최종 스냅샷 |

RDS는 `publicly_accessible=false`이며 전용 private subnet을 새로 만드는 구성은 아닙니다. 외부에서 DB 5432 포트를 열지 않습니다. 호스트의 앱 점검 포트 18080·13000도 `127.0.0.1`에만 바인딩됩니다.

## 2. 준비물과 비용 확인

- AWS 계정과 서울 리전의 EC2·RDS 생성 권한. 조직 계정이면 SCP에서도 서울 사용이 허용되어야 합니다.
- GitHub 공개 저장소. 이 배포는 GitHub의 `ubuntu-24.04-arm` runner를 사용합니다.
- 도메인 또는 DuckDNS 서브도메인. 기존 `aidlink.duckdns.org` 대신 본인 주소를 준비합니다.
- Git, AWS CLI v2, Terraform 1.10 이상, OpenSSH. S3 잠금 파일을 쓰므로 이 안내에서는 Terraform 1.10 이상을 사용합니다.
- 정부24 실제 데이터 수집 시 공공데이터포털 OpenAPI 인증키.

아래 터미널 예시는 **Bash 기준**입니다. Windows에서는 WSL의 Bash에서 실행할 수 있습니다. AWS CLI 인증과 SSH 키는 명령을 실행하는 환경 안에서 준비합니다. Docker·Java·Node.js는 로컬에 설치하지 않아도 Actions로 빌드할 수 있습니다.

EC2 실행, EBS, RDS 실행·저장소, 공인 IPv4, S3 등에 비용이 발생할 수 있습니다. 크레딧 잔액·만료일과 서비스별 무료 혜택은 **본인 계정의 Billing에서 확인**합니다. EC2를 정지해도 EBS·Elastic IP·RDS 등 남은 리소스의 비용이 모두 중단되는 것은 아닙니다.

## 3. GitHub 저장소와 AWS 인증 준비

1. [sunmin-hwang/aidlink](https://github.com/sunmin-hwang/aidlink)를 본인 계정으로 Fork합니다.
2. Fork 저장소의 Actions 사용을 활성화합니다.
3. 저장소를 내려받습니다.

```bash
git clone https://github.com/YOUR_GITHUB_ID/aidlink.git
cd aidlink
```

AWS CLI는 본인의 SSO·로그인 방식에 맞게 인증합니다. 예를 들어 `aws login`을 사용하는 계정은 다음과 같이 실행합니다.

```bash
aws login --profile aidlink
aws sts get-caller-identity --profile aidlink
# 현재 터미널에 임시 자격 증명을 전달합니다. 출력값을 문서·로그에 복사하지 않습니다.
eval "$(aws configure export-credentials --profile aidlink --format env)"
```

Terraform은 위에서 내보낸 임시 자격 증명을 사용합니다. `aws login` 세션을 직접 읽지 못하는 SDK에서도 이 방법을 사용할 수 있습니다. 인증이 만료되면 로그인·내보내기를 다시 실행합니다. 작업 후에는 `unset AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_SESSION_TOKEN`으로 현재 터미널에서 제거합니다.

이후 나오는 계정 ID를 기록합니다. Terraform의 `aws_account_id`에 같은 ID를 지정해야 합니다. 저장소 기본값은 기존 운영 계정이므로 **반드시 본인 계정 ID로 덮어씁니다.**

SSH 키가 없다면 생성합니다. 개인키를 Git에 넣지 않습니다.

```bash
ssh-keygen -t ed25519 -f ~/.ssh/aidlink
```

## 4. Terraform 상태용 S3 준비

Terraform으로 만들 앱 인프라와 별도로, 상태를 저장할 S3 버킷을 먼저 만듭니다. 버킷 이름은 전 세계에서 고유해야 합니다.

1. AWS S3 콘솔에서 서울 리전의 버킷을 만듭니다. 예: `YOUR_UNIQUE_AIDLINK_TFSTATE`.
2. **모든 퍼블릭 액세스 차단**을 켜고 유지합니다.
3. 기본 암호화(SSE-S3 등)와 버킷 버전 관리를 활성화합니다.
4. Terraform 실행 주체에 해당 상태 경로 읽기·쓰기와 잠금 파일 생성·삭제 권한을 부여합니다.

`infra/aws/terraform/backend.hcl`을 로컬에서 만듭니다.

```hcl
bucket       = "YOUR_UNIQUE_AIDLINK_TFSTATE"
key          = "production/terraform.tfstate"
region       = "ap-northeast-2"
encrypt      = true
use_lockfile = true
```

이 버킷은 아래 Terraform이 자동 생성하지 않습니다. 상태에 DB 비밀번호가 포함될 수 있으므로 다운로드·공유 권한도 제한합니다. `backend.hcl`, tfvars, 상태 파일과 plan 파일은 Git에 올리지 않습니다.

## 5. EC2와 RDS 생성

`infra/aws/terraform/production.tfvars`를 만들고 본인의 값을 입력합니다.

```hcl
aws_profile       = null
aws_account_id    = "YOUR_12_DIGIT_AWS_ACCOUNT_ID"
region            = "ap-northeast-2"
availability_zone = "ap-northeast-2a"
ssh_allowed_cidr  = "YOUR_PUBLIC_IPV4/32"
ssh_public_key    = "ssh-ed25519 YOUR_PUBLIC_KEY_CONTENT"
db_username       = "aidlink_admin"
db_password       = "YOUR_LONG_RANDOM_DB_PASSWORD"
```

`ssh_public_key`에는 `~/.ssh/aidlink.pub`의 내용 전체를 넣습니다. `ssh_allowed_cidr`에는 유지보수 PC의 실제 외부 IPv4와 `/32`를 넣고, SSH를 `0.0.0.0/0`으로 열지 않습니다. DB 비밀번호는 RDS가 허용하는 문자와 길이로 정합니다.

```bash
cd infra/aws/terraform
terraform init -backend-config=backend.hcl
terraform fmt -check
terraform validate
terraform plan -var-file=production.tfvars -out=production.tfplan
# 계정·리전·생성 리소스·비용을 확인한 뒤 실행
terraform apply production.tfplan
terraform output
cd ../../..
```

출력에서 다음을 기록합니다.

| 출력 | 용도 |
| --- | --- |
| `public_ip` | 도메인의 A 레코드, SSH 접속 |
| `database_endpoint` | EC2에서 DB 연결·백업 |
| `jdbc_url` | 서버 `AIDLINK_DB_URL` |

EC2 user-data가 Docker·Compose·swap과 `/opt/aidlink` 디렉터리를 준비합니다. 처음에는 수 분 걸릴 수 있습니다. EC2 콘솔의 인스턴스 상태 검사를 확인하고 SSH로 접속합니다. 최초 SSH 접속 시 호스트 키 지문을 EC2 콘솔 등 신뢰할 수 있는 경로와 대조합니다.

```bash
ssh -i ~/.ssh/aidlink ubuntu@YOUR_EC2_PUBLIC_IP
sudo cloud-init status --wait
docker version
docker compose version
free -h
```

Docker 소켓 권한 오류가 나면 로그아웃 후 다시 SSH로 접속합니다. 초기화 실패 시 `sudo tail -n 100 /var/log/cloud-init-output.log`를 확인합니다.

## 6. 서버 환경 변수와 도메인 준비

서버에서 환경 파일을 만듭니다. 다음 명령은 **EC2 안에서** 실행합니다.

```bash
cd /opt/aidlink
umask 077
nano .env.aws
```

최소 입력 내용:

```dotenv
AIDLINK_DOMAIN=YOUR_DOMAIN
AIDLINK_DB_URL=jdbc:postgresql://YOUR_RDS_ENDPOINT:5432/aidlink?sslmode=require
AIDLINK_DB_USERNAME=aidlink_admin
AIDLINK_DB_PASSWORD=YOUR_RDS_PASSWORD
AIDLINK_GOV24_SERVICE_KEY=
AIDLINK_GOV24_SCHEDULING_ENABLED=false
AIDLINK_GOV24_DAILY_TIME=03:00
```

```bash
chmod 600 /opt/aidlink/.env.aws
```

- `AIDLINK_DOMAIN`은 `https://`나 경로 없이 호스트 이름만 입력합니다.
- DB URL은 Terraform의 `jdbc_url` 출력과 일치시킵니다. 비밀번호는 URL에 넣지 않습니다.
- 정부24 키는 실제 수집을 준비한 뒤 넣을 수 있습니다.
- 공개 프론트엔드 변수나 GitHub 코드·Actions 로그에 DB 비밀번호와 API 키를 넣지 않습니다.
- 경량 Compose가 JVM, DB 풀, Secure Cookie와 동일 출처 API 구성을 설정합니다. `.env.aws.example`의 전체 모니터링용 항목은 경량 배포의 필수 입력이 아닙니다.

DuckDNS 대시보드에서 본인 서브도메인의 IPv4를 Terraform의 `public_ip`로 바꾸고 저장합니다. 별도 DNS 서비스라면 도메인의 A 레코드를 같은 IP로 설정합니다. 사용하지 않는 AAAA 레코드가 다른 서버를 가리키지 않는지도 확인합니다.

```bash
nslookup YOUR_DOMAIN
```

이후 앱을 시작하면 Caddy가 해당 도메인의 인증서를 발급합니다. DNS와 EC2 보안 그룹의 80/443 접근이 정상이어야 합니다. Caddy `/data` 볼륨은 인증서 저장소이므로 재배포 때 삭제하지 않습니다.

## 7. EC2에 GitHub 배포 runner 설치

이미지 빌드는 GitHub 제공 ARM runner가 담당합니다. EC2에는 **배포 전용 self-hosted runner**를 설치합니다.

1. 본인 저장소의 **Settings → Actions → Runners → New self-hosted runner**를 엽니다.
2. 운영체제 **Linux**, 아키텍처 **ARM64**를 선택합니다.
3. 안내된 공식 runner 다운로드·압축 해제 명령을 EC2의 `/home/ubuntu/actions-runner`에서 실행합니다. GitHub가 표시하는 최신 다운로드 주소·체크섬을 사용합니다.
4. `ubuntu` 사용자로 안내된 `./config.sh` 명령을 실행합니다. 저장소 URL은 본인 Fork이며, runner 이름은 예를 들어 `aidlink-seoul`로 정합니다.
5. 추가 label에 **`aidlink-deploy`**를 입력합니다. 기본 `self-hosted`, `Linux`, `ARM64` label도 유지합니다.
6. 일회성 등록 토큰은 GitHub 화면에서 확인해서 사용하고 저장소나 문서에 기록하지 않습니다.
7. runner 디렉터리에서 서비스로 등록합니다.

```bash
sudo ./svc.sh install ubuntu
sudo ./svc.sh start
sudo ./svc.sh status
```

GitHub Runners 목록에서 **Idle / Online** 상태를 확인합니다. runner의 `ubuntu` 사용자가 Docker를 실행하고 `/opt/aidlink`에 파일을 쓸 수 있어야 합니다. PR의 임의 코드를 이 runner에서 실행하는 워크플로를 추가하지 않습니다.

## 8. GitHub Actions 최초 배포

1. 저장소 **Settings → Environments**에서 `production`을 만들고 배포 가능 브랜치를 `main`으로 제한합니다.
2. **Settings → Secrets and variables → Actions → Variables**에 다음 Repository variables를 만듭니다.

| 이름 | 값 |
| --- | --- |
| `AWS_DEPLOY_ENABLED` | `true` |

3. Fork의 Actions 정책에서 저장소에 포함된 GitHub·Docker Actions 실행을 허용합니다. GHCR 로그인·게시에는 워크플로가 권한을 지정한 `GITHUB_TOKEN`을 사용하므로 별도 장기 PAT나 DB 비밀값을 Actions에 등록하지 않습니다.
4. **Actions → Deploy AWS → Run workflow → main**을 선택합니다.
5. `build (backend)`, `build (frontend)`, `deploy`가 모두 성공하는지 확인합니다.

워크플로는 ARM64 이미지를 GHCR에 게시하고, digest로 특정한 이미지를 EC2에 배포합니다. Fork에서도 이미지 주소는 `ghcr.io/본인계정/저장소이름-backend` 및 `-frontend` 형식으로 자동 계산합니다. GHCR 패키지 권한 오류가 나면 패키지와 저장소의 Actions 접근 연결을 확인합니다.

배포 단계는 Compose·Caddyfile·배포 스크립트를 `/opt/aidlink`에 복사하고 다음 파일을 생성·관리합니다.

| 서버 파일 | 역할 |
| --- | --- |
| `.env.aws` | 운영 DB·API 키·도메인. 운영자가 별도로 관리 |
| `.env.images` | 현재 backend/frontend 이미지 참조 |
| `.env.images.previous` | 직전 이미지 참조 |
| `docker-compose.aws-lean.yml` | 실행 서비스와 자원 제한 |
| `infra/aws/deploy.sh` | 교체, 상태 검사, 실패 시 이전 이미지 복구 |

새 DB는 최초 백엔드 실행 때 Flyway가 스키마와 샘플 데이터를 준비합니다. 별도로 테이블을 수동 생성하지 않습니다. 앱을 재배포해도 RDS 데이터는 유지됩니다.

## 9. 배포 결과 확인

EC2에서 컨테이너와 내부 상태를 점검합니다.

```bash
cd /opt/aidlink
docker compose --env-file .env.aws --env-file .env.images -f docker-compose.aws-lean.yml ps
curl -fsS http://127.0.0.1:18080/actuator/health/readiness
curl -I http://127.0.0.1:13000/auth
docker stats --no-stream
```

readiness 응답은 `{"status":"UP"}`이어야 합니다. 외부에서는 다음을 확인합니다.

```bash
curl -I https://YOUR_DOMAIN/auth
curl -fsS 'https://YOUR_DOMAIN/api/v1/policies/search?page=0&size=5'
```

브라우저에서는 랜딩 페이지, 로그인, 정책 목록, 검색 버튼과 검색 조건 변경, 상세 링크, 프로필 조회·저장, 로그아웃을 실제로 확인합니다. 프로필 저장 검증은 본인의 테스트 계정으로 진행합니다. 운영 계정의 데이터를 시험 삼아 바꾸지 않습니다.

세션·CSRF를 사용하는 변경 요청은 쿠키와 CSRF 토큰이 모두 필요합니다. 로그인 전 `GET /api/v1/auth/csrf`로 발급하고 응답의 `headerName`과 `token`을 사용합니다. 단순 HTTP 200이나 컨테이너 실행 상태만으로 전체 화면 동작 검증을 대체하지 않습니다.

외부의 `/actuator/*`는 Caddy가 의도적으로 404를 반환합니다. 위 내부 loopback 주소로 상태를 검사합니다. `NEXT_PUBLIC_API_BASE_URL`을 별도 외부 백엔드 주소로 바꾸지 않고 동일 도메인의 `/api/*`를 사용합니다.

## 10. 정부24 정책 수집

### 인증키 준비

1. [공공데이터포털의 대한민국 공공서비스 정보 API](https://www.data.go.kr/data/15113968/openapi.do)를 활용 신청합니다.
2. 승인된 일반 인증키의 **Decoding 값**을 서버 `.env.aws`의 `AIDLINK_GOV24_SERVICE_KEY`에 넣습니다. 이 프로젝트는 `/gov24/v3/serviceList`, `/gov24/v3/serviceDetail`을 사용합니다.
3. 실제 수집 준비가 끝나면 `AIDLINK_GOV24_SCHEDULING_ENABLED=true`로 바꿉니다.
4. 다음 명령으로 백엔드 컨테이너를 다시 생성합니다. 단순 `restart`만으로 환경 파일 변경이 반영되지는 않습니다.

```bash
cd /opt/aidlink
docker compose --env-file .env.aws --env-file .env.images -f docker-compose.aws-lean.yml up -d --no-build --force-recreate backend
```

### 실행 시각과 최초 수집

백엔드는 **한국시간 `Asia/Seoul` 기준 매일 03:00 이후** 그날 완료된 수집이 있는지 매분 확인합니다. 처음 켠 시각이 03:00 이후이고 그날 성공 이력이 없으면 시작 후 수집합니다. 03:00 이전에 켰다면 해당 시각까지 기다립니다. 서버의 로그 시간이 UTC여도 수집 기준 시간대는 한국시간입니다.

따라서 새 DB의 최초 실제 데이터 수집도 위 설정으로 시작할 수 있습니다. 기존 DB를 이전한 경우 오늘의 `COMPLETED` 이력이 이미 있으면 같은 날 다시 수집하지 않습니다. 상태 API는 읽기 전용이며 강제로 수집하는 API가 아닙니다.

```bash
curl -fsS https://YOUR_DOMAIN/api/v1/collections/gov24/status
```

`enabled=true`, 한국 날짜의 `collectionDate`, `status=COMPLETED`, `executionId`, `readCount`·`writeCount`를 확인합니다. 과거 이력만 보고 새 서버에서 수집했다고 판단하지 말고 실행 ID·시작/종료 시각·서버 로그를 함께 확인합니다.

EC2에서 로그를 확인합니다.

```bash
cd /opt/aidlink
docker compose --env-file .env.aws --env-file .env.images -f docker-compose.aws-lean.yml logs --tail=200 backend
```

RDS에 접속할 수 있는 운영 도구에서는 다음 SQL로 확인할 수 있습니다. DB는 EC2 내부에서 접속하거나 SSH 터널을 사용하며 외부에 공개하지 않습니다.

```sql
SELECT job_execution_id, status, start_time, end_time, exit_code
FROM batch_job_execution
ORDER BY job_execution_id DESC
LIMIT 5;

SELECT COUNT(*) AS government_policy_count
FROM policy
WHERE external_id LIKE 'GOV24:%';
```

## 11. 수정과 재배포

```text
dev에서 수정·검증 → dev push → main으로 병합
→ main CI 성공 → Deploy AWS → 공개 사이트 동작 확인
```

`dev` push는 CI를 실행하지만 이 AWS 운영 배포를 시작하지 않습니다. main의 CI 성공 후 빌드·배포하며 수동 실행도 main에서만 가능합니다. ARM 빌드는 GitHub에서 병렬 처리하고 Docker 캐시를 재사용하므로 2GiB EC2 안에서 Gradle/npm 빌드를 하지 않습니다.

배포 스크립트는 백엔드 readiness와 프론트 HTTP를 확인하고 실패하면 이전 이미지로 복구를 시도합니다. **이미지 복구는 DB 마이그레이션을 되돌리지 않습니다.** DB 변경 전 백업과 이전 앱과의 호환성을 검토합니다. 백엔드 교체 후 기존 로그인 세션은 만료될 수 있습니다.

인프라 변경은 앱 push와 별개입니다. Terraform `plan`을 검토하고 필요한 변경만 `apply`합니다. 사용자 정의 AMI가 아니라 최신 Ubuntu AMI를 조회하므로 시간이 지난 뒤 plan에서 EC2 교체가 제안될 수 있습니다. `prevent_destroy`를 무심코 해제하지 않습니다.

## 12. 문제 해결과 운영 종료

| 증상 | 확인할 내용 |
| --- | --- |
| Terraform 계정 가드 오류 | `sts get-caller-identity`와 `aws_account_id`, CLI 프로필 확인 |
| 서울 리전 AccessDenied | IAM 권한과 조직 SCP의 서울 허용 여부 확인 |
| S3 상태·잠금 오류 | 버킷 리전, 실행 프로필, 객체 및 잠금 파일 권한, Terraform 버전 확인 |
| SSH 접속 실패 | EIP, 개인키, 유지보수 PC 공인 IP와 `/32` 규칙 확인 |
| runner가 Offline / 배포가 대기 | EC2 runner 서비스, Linux·ARM64·aidlink-deploy label과 production 환경 제한 확인 |
| GHCR 게시·다운로드 거부 | Actions packages 권한, 패키지 저장소 연결과 해당 실행의 로그 확인 |
| DB 연결 실패 | RDS Available 상태, endpoint·사용자·비밀번호, SG와 JDBC TLS 설정 확인 |
| Flyway 실패 | 최초 DB 오류와 이력 확인. 기존 migration SQL·이력 테이블을 임의 삭제하지 않음 |
| HTTPS 인증서 실패 | DNS A/AAAA, 80/443 접근, Caddy 로그와 발급 제한 확인 |
| API 502 또는 로그인 실패 | backend readiness, 컨테이너 로그, DB 연결과 Secure Cookie·동일 도메인 설정 확인 |
| 프로필 저장·로그아웃 403 | 다시 로그인하고 CSRF 토큰·세션 쿠키 확인 |
| 수집이 DISABLED | 서버 환경 파일의 수집 활성화 값과 컨테이너 재생성 여부 확인 |
| 정부24 401/403 | 활용 승인, 인증키 Decoding 값과 공백 확인 |
| 느린 응답·메모리 부족 | `docker stats`, `free -h`, DB 연결 대기·쿼리와 EC2 CPU Credit 확인 |

운영 종료 시 먼저 DB를 백업합니다. 이 Terraform에는 EC2·RDS `prevent_destroy`와 RDS 삭제 보호가 있으므로 일반 `terraform destroy`로 바로 지워지지 않습니다. 보관할 스냅샷과 상태를 확인한 후 보호 해제와 제거 계획을 검토합니다. 앱 컨테이너를 중지하는 것만으로 AWS 리소스 비용이 종료되지는 않습니다. 남은 EIP·EBS·RDS·스냅샷·S3와 runner 등록도 확인합니다.
