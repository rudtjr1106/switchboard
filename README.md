<div align="center">

<img src="app/src/main/resources/icon.png" width="96" alt="스위치보드">

# 스위치보드

<sub>Switchboard</sub>

**Android 원격 설정(remote config) 편집기 · macOS / Windows**

[**최신 버전 내려받기**](https://github.com/rudtjr1106/switchboard/releases/latest) · [**사용법 (Android 팀)**](docs/사용법.md) · macOS: `Switchboard.dmg` · Windows: `Switchboard-windows.zip`

</div>

앱을 새로 배포하지 않고도 특정 화면에 안내 다이얼로그를 켜고 끄고, 점검 공지를 띄우고, 강제 업데이트 기준을 올립니다.
설정은 GitHub 저장소의 `app-config.json` 하나이고, 앱은 GitHub Pages 로 서빙되는 그 파일을 읽습니다. 스위치보드는 그 저장소를 **폼으로 편집하고, PR → 검사 → 머지 → 배포**까지 대신 진행하는 데스크톱 앱입니다.

[UMC-PRODUCT/umc-product-iOS-remote-config](https://github.com/UMC-PRODUCT/umc-product-iOS-remote-config) 의 macOS 편집 앱(UMC Launchpad)에서 착안했고, [umc-product-android-config](https://github.com/UMC-PRODUCT/umc-product-android-config) 와 같은 저장소 형식을 씁니다.

![스위치보드 편집 화면. 왼쪽 사이드바에서 안내를 고르고 가운데 폼에서 값을 고치고 오른쪽 Android 미리보기로 모양을 확인합니다](docs/images/editor.png)

| 로그인 | 적용 확인 (모든 화면 차단 경고) |
|---|---|
| ![GitHub 로그인 화면](docs/images/login.png) | ![변경 사항 적용 시트](docs/images/apply-dialog.png) |

## 할 수 있는 것

| | |
|---|---|
| **GitHub 로그인 한 번으로 시작** | OAuth Device Flow(브라우저에서 8자리 코드 입력)로 로그인하면 끝. `gh` CLI 토큰이나 PAT 도 됩니다 |
| **저장소 자동 세팅** | "새 저장소 만들기" 를 누르면 `app-config.json` · `schema.json` · validate 워크플로 · README · GitHub Pages · main 보호 규칙까지 한 번에 만듭니다 |
| **폼 편집 + Android 미리보기** | 화면·모양·문구·종료일을 폼으로 고치고 오른쪽 폰 미리보기로 확인. 글자 수 초과, 잘못된 날짜, 스키마에 없는 화면은 입력 즉시 표시됩니다 |
| **안전한 적용** | 충돌 확인 → 브랜치 → 커밋 → PR → `validate` 검사 → 스쿼시 머지 → Pages 배포 확인. 머지 전에 실패하면 PR 과 브랜치를 정리합니다. 모든 화면을 막는 차단 안내는 빨간 경고와 함께 별도 버튼으로만 켜집니다 |
| **스키마 기반** | 화면 목록·글자 수 제한·`minimumVersion` 지원 여부를 저장소의 `schema.json` 에서 읽습니다. 저장소마다 규칙이 달라도 앱을 고칠 필요가 없습니다 |
| **Android 프로젝트 세팅** | 로컬 Android 프로젝트를 AI 없이 스캔해 내비게이션 목적지(타입 세이프·Navigation 3·문자열 route·XML 그래프), DI(Hilt·Koin·Dagger), HTTP(Retrofit·Ktor)를 알아냅니다. `build-logic` 컨벤션 플러그인도 따라갑니다. 그 프로젝트에 맞는 연동 코드를 만들고, 알아보지 못한 부분은 온디바이스 AI 가 프로젝트의 실제 코드를 보고 고쳐 씁니다. 화면 목록은 저장소 `schema.json` 에 PR 로 반영합니다 |
| **온디바이스 AI** | llama.cpp + Gemma 3 를 이 컴퓨터에서 돌립니다. 안내 문구 다듬기·상황 설명으로 초안 만들기, 화면 이름에 한국어 라벨·구분 붙이기. 문구가 밖으로 나가지 않습니다 |

## 왜 Firebase Remote Config 가 아닌가

둘 다 쓸 수 있고 겹치지도 않습니다. 이 도구는 **전체 사용자에게 같은 값을 주는 운영 스위치**(점검 공지, 강제 업데이트, 특정 화면 차단)만 다룹니다. 그 범위에서 다른 점은 이렇습니다.

| | 스위치보드 | Firebase Remote Config |
|---|---|---|
| **값 검증** | `schema.json` 이 화면 이름을 enum 으로, 글자 수를 `maxLength` 로 강제합니다. 편집기에서 막히고, PR 의 `validate` 검사가 한 번 더 막습니다 | 값이 문자열·JSON 자유형이라 오타는 앱에서 드러납니다 |
| **변경 이력** | PR 로 남습니다. 리뷰어 지정·머지 권한 같은 GitHub 권한을 그대로 씁니다 | 콘솔에 버전 이력과 롤백이 있습니다. 승인 절차를 강제하기는 어렵습니다 |
| **화면 목록** | 프로젝트를 스캔해 `Route` 에서 뽑아 `schema.json` 에 넣습니다. 이름이 바뀌면 다시 스캔해 맞춥니다 | 콘솔에 문자열을 직접 적습니다. Route 이름이 바뀌어도 알려주지 않습니다 |
| **앱 쪽 코드** | 데이터·도메인·UI·DI 13개 파일을 프로젝트의 DI·HTTP 스택에 맞춰 만들어 줍니다 | SDK 추가, `fetchAndActivate`, 기본값 리소스, 다이얼로그 UI 를 직접 짭니다 |
| **의존성** | 이미 쓰는 Retrofit 이나 Ktor 로 JSON 하나를 받습니다. SDK 가 늘지 않고 식별자도 수집하지 않습니다 | Firebase SDK 와 초기화가 붙고, 수집하는 식별자를 데이터 보안 항목에 적어야 합니다 |
| **편집 화면** | Android 미리보기, 글자 수·말투 검사, 온디바이스 AI 초안 | 값 입력 폼 |

**Firebase 가 나은 쪽**도 분명합니다. 국가·앱 버전·사용자 속성별 타깃팅과 퍼센트 롤아웃, A/B 테스트, 리스너로 즉시 반영, 콘솔 원클릭 롤백은 여기에 없습니다. 이 도구는 Pages 배포에 1~3분이 걸리고, 앱이 포그라운드로 올라올 때 값을 다시 읽습니다.

실험이나 세그먼트별 값이 필요하면 Firebase 를 쓰고, 운영 스위치는 이쪽으로 나누는 구성이 무난합니다.

## 설치

1. [Releases](https://github.com/rudtjr1106/switchboard/releases/latest) 에서 `Switchboard.dmg`(macOS) 또는 `Switchboard-windows.zip`(Windows)을 받습니다
2. macOS: 열린 창에서 `스위치보드.app` 을 `Applications` 로 끌어다 놓습니다. Windows: zip 을 원하는 곳에 풀고 `Switchboard.exe` 를 실행합니다 (설치 과정이 없습니다)
3. 처음 켜면 GitHub 로그인 화면이 나옵니다

앱은 켜질 때 새 릴리즈가 있는지 확인하고 위쪽에 알림 띠를 띄웁니다. JVM 이 함께 들어 있어 따로 설치할 것은 없습니다.

### GitHub 로그인 방식

| 방식 | 필요한 것 |
|---|---|
| 브라우저 로그인 (추천) | 없음. **GitHub 으로 로그인** 을 누르고 브라우저에 8자리 코드를 넣으면 끝납니다 |
| gh CLI | `brew install gh` + `gh auth login`. 앱이 `gh auth token` 으로 토큰을 빌려 씁니다 |
| Personal Access Token | `repo` · `workflow` · `read:org` 스코프 |

브라우저 로그인은 GitHub OAuth App 으로 진행됩니다. Client ID 는 `gradle.properties` 의 `switchboard.githubClientId` 에 들어 있고 비밀값이 아닙니다.

- 조직이 서드파티 OAuth 앱 접근을 제한하면, 처음 로그인할 때 승인 화면의 조직 옆 **Request** 를 눌러 관리자 승인을 받아야 그 조직 저장소를 쓸 수 있습니다
- 포크해서 자기 이름으로 배포하려면 OAuth App 을 새로 등록하고(Enable Device Flow 체크, Expire user access tokens 해제) `gradle.properties` 의 Client ID 를 바꾸세요

## 저장소 형식

스위치보드가 만드는(그리고 읽는) 설정 저장소는 아래 네 파일로 이뤄집니다.

| 파일 | 설명 |
|---|---|
| `app-config.json` | 실제 설정. 앱이 `https://<owner>.github.io/<repo>/app-config.json` 에서 읽습니다 (캐시 10분) |
| `schema.json` | 값의 규칙. `screen` enum, `template` enum, 글자 수 제한, `minimumVersion` 패턴. 편집기가 이 파일을 읽어 폼을 만듭니다. `x-switchboard.screens` 에 화면의 한국어 이름과 구분을 둡니다 |
| `.github/workflows/validate.yml` | PR 마다 `jsonschema` 로 검사. job 이름 `validate` 를 앱이 기다립니다 |
| `README.md` | 운영진용 안내 |

```json
{
  "$schema": "./schema.json",
  "version": 1,
  "minimumVersion": "",
  "notices": [
    {
      "screen": "ALL",
      "enabled": false,
      "template": "BLOCKING",
      "title": "서비스 점검 중이에요",
      "body": "더 나은 서비스를 위해 점검하고 있어요. 잠시 후 다시 이용해주세요.",
      "until": "2026-09-20"
    }
  ]
}
```

이 앱으로 만들지 않은 저장소도 `app-config.json` 과 `schema.json` 이 있으면 "주소로 열기" 로 열립니다.

## 업데이트

앱이 뜰 때 새 릴리즈를 확인하고, 있으면 위쪽에 띠로 알립니다. **지금 업데이트** 를 누르면 브라우저를 열지 않고 앱이 직접 받아서 바꿔 끼운 뒤 다시 시작합니다.

- macOS: 받은 DMG 안의 앱을 `codesign` · `spctl` 로 검사해 **Apple 공증을 통과했고 지금 앱과 같은 팀이 서명한** 것만 설치합니다. 번들을 통째로 바꾸므로 서명과 공증 티켓이 그대로 유지됩니다
- Windows: zip 의 SHA-256 을 릴리즈의 `SHA256SUMS.txt` 와 맞춰 본 뒤, 앱이 꺼지기를 기다렸다가 폴더를 바꿔 끼우는 스크립트를 띄웁니다 (도는 중인 `Switchboard.exe` 는 자기 자신을 지울 수 없기 때문입니다)

앱 안의 jar 만 바꾸는 델타 방식은 쓰지 않습니다. 서명 봉인(CodeResources)이 깨져 macOS 가 앱을 더 이상 검증하지 못하게 됩니다.

## 알려진 제약

- Windows 배포본은 서명하지 않아 처음 열 때 SmartScreen 경고가 뜹니다 (macOS 는 서명·공증합니다). 대신 릴리즈의 `SHA256SUMS.txt` 로 파일이 바뀌지 않았는지 확인할 수 있고, 앱의 자동 업데이트도 이 값을 맞춰 본 뒤에만 설치합니다
- java-llama.cpp 4.2.0 은 모델을 내려도 메모리를 완전히 돌려주지 않습니다 (4B → 1B 전환 시 이전 모델이 상주). 모델을 바꾸면 앱을 다시 켜는 편이 안전합니다
- 번들된 llama.cpp(b4916)가 아는 아키텍처만 씁니다. Gemma 3 는 되고 Qwen3 는 로드되지 않습니다

## 쓰인 자료

- GitHub 로그인 버튼의 마크: [Primer Octicons](https://github.com/primer/octicons) `mark-github-24` (MIT License, © GitHub Inc.). [GitHub 로고 사용 규칙](https://github.com/logos)에 따라 모양을 바꾸지 않고 단색으로 씁니다

## 라이선스

[PolyForm Strict License 1.0.0](LICENSE). 소스는 공개하지만 **배포·수정·2차 저작물은 허용하지 않습니다.** 그 밖의 용도가 필요하면 저장소 주인에게 문의해 주세요.

v1.0.3 까지의 릴리즈는 MIT 로 공개했고, 그 버전들에 준 권리는 철회되지 않습니다. 이 라이선스는 그 이후 버전부터 적용됩니다.
