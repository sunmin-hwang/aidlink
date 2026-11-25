# AIDLINK AWS 경량 배포

서울 EC2 `t4g.small`(ARM64, 2GiB)에 Caddy·Next.js·Spring Boot를 실행하고, PostgreSQL은 비공개 RDS `db.t4g.micro`로 분리한다. 기존 전체 모니터링 파일 대신 `docker-compose.aws-lean.yml`을 사용한다. 서비스의 실제 전환 상태와 주소는 배포 완료 후 기록한다.

## 인프라와 비밀값

- Terraform: `infra/aws/terraform`. 계정 ID 가드, 같은 AZ의 EC2/RDS, EC2 gp3 20GB·RDS gp3 20GB, EIP 1개를 사용한다. RDS의 public access는 비활성화하고 앱 SG만 5432로 허용한다.
- SSH는 유지보수 PC의 `/32` 주소만 허용한다. 웹에는 80/443을 공개하며, 앱 18080/13000은 호스트 loopback에만 바인딩한다.
- Terraform 상태는 비공개·암호화·버전 관리 S3와 잠금 파일로 관리한다. `backend.hcl`, tfvars, plan, 상태와 비밀번호는 Git에 넣지 않는다. S3 상태에도 비밀번호가 포함될 수 있으므로 접근을 제한한다.
- RDS는 7일 자동 백업·삭제 보호·최종 스냅샷을 사용한다. `prevent_destroy`를 해제하는 작업은 별도 검토한다. 기본 VPC 서브넷을 사용하며 전용 private subnet을 만든 구성은 아니다.
- 운영 키는 서버 `/opt/aidlink/.env.aws`에 두고 권한을 `600`으로 설정한다. DB URL/사용자/비밀번호, 정부24 키, 서비스 도메인 등을 지정한다. 프론트 및 이미지 빌드에 비밀키를 전달하지 않는다.
- 초기 복원·검증 동안 `AIDLINK_GOV24_SCHEDULING_ENABLED=false`로 유지하고 최종 전환 후 `true`로 설정한다. 한국 시간 `03:00`이 기준이다. 현재 운영의 임시 회원가입 차단도 유지한다.

## Actions 최적화

효자손 프로젝트의 self-hosted 배포·동시 실행 방지·로그 회전을 참고했다. AIDLINK는 프론트도 같은 2GiB 서버에 있어 서버 내 이미지 빌드는 사용하지 않는다.

1. `dev`에서는 개발·CI 검증을 수행한다. `main` push의 CI 성공 후 `Deploy AWS`가 시작된다. `workflow_dispatch`는 main에서만 가능하다.
2. 공개 저장소의 GitHub 표준 `ubuntu-24.04-arm` runner가 프론트·백엔드를 병렬 빌드한다. Docker GHA 캐시를 재사용하고 GHCR에 commit SHA 태그로 게시한다. 배포에는 태그 대신 digest를 전달한다.
3. EC2의 `aidlink-deploy` runner는 이미지를 내려받고 Compose 교체·백엔드 readiness·프론트 HTTP 확인만 수행한다. 앱 Gradle/npm 빌드를 실행하지 않는다.
4. 동시 배포는 Actions concurrency와 서버 `flock`으로 막는다. `.env.images.previous`로 이전 이미지 참조를 보존하며 readiness 실패 시 이미지를 되돌린다. DB 마이그레이션은 이미지 롤백으로 취소되지 않으므로 호환되는 변경과 별도 DB 복원 계획이 필요하다.
5. `AWS_DEPLOY_ENABLED=true`일 때만 배포한다. 첫 전환 검증 전에는 `false`로 유지한다. 운영 environment 이름은 `production`이다.

빌드에는 job 한정 `GITHUB_TOKEN`의 packages write, 배포에는 packages read를 사용한다. 서버 DB/API 비밀번호를 Actions Secrets에 복제하지 않는다. registry 인증 파일은 runner 임시 디렉터리에 만들고 job 종료 시 제거한다. self-hosted runner는 main의 신뢰된 배포에만 사용하며 PR 작업을 연결하지 않는다.

## 최초 배포와 검증

1. Terraform init/validate/plan으로 계정·리전·비용과 공개 범위를 검토한 뒤 apply한다. 예시 프로필을 그대로 재사용하지 않는다.
2. Neon 원본을 변경하지 않고 백업한다. Neon의 `pg_attver` 컬럼 내부 옵션은 RDS에서 지원하지 않으므로 원본 보존 후 호환 SQL에서 해당 옵션만 제거한다. `public` 스키마만 추출했다면 `pg_trgm` 확장을 명시적으로 준비한다.
3. 새 DB에 복원하고 모든 public 테이블의 행 수·내용 해시, Flyway 이력을 원본과 대조한다. 기존 서비스는 검증 중 유지한다.
4. 이미지는 EC2 밖에서 ARM64로 빌드한다. 최초 수동 배포는 로컬 `docker save`/SSH 전송도 가능하며 이후에는 Actions를 사용한다.
5. 서버에 Compose·Caddyfile·`infra/aws/deploy.sh`를 배치한다. `.env.images`는 backend/frontend 이미지 참조만 포함한다. 실행 예:

   ```bash
   bash /opt/aidlink/infra/aws/deploy.sh BACKEND_IMAGE_REFERENCE FRONTEND_IMAGE_REFERENCE
   ```

6. 로그인·세션·검색·상세·프로필 표시를 검증하고 DNS를 전환해 HTTPS 인증서를 확인한다. 보호 데이터에 차이가 있으면 전환하지 않는다. 검증 후 AWS 일일 수집을 활성화하고 Render 중복 실행을 막는다.
7. 30명 시연 전 로그인/검색/상세 흐름으로 별도 부하 검증한다. 기동 성공이나 container running만으로 처리 성능을 확정하지 않는다.

GitHub 표준 ARM runner: https://docs.github.com/en/actions/reference/runners/github-hosted-runners
