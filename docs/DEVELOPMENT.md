# 개발 안내

설정 도구는 C# / WPF, SQL Developer 내부 기능은 Java 에이전트로 구현되어 있습니다.

## 빌드

Windows, .NET Framework 4.8 C# 컴파일러, JDK 17 또는 21, SQL Developer **26.2.0.186.2220** 설치본이 필요합니다.

```powershell
.\build.ps1 -SqlDeveloperPath "C:\dev\sqldeveloper"

# JDK 경로를 지정하려면
.\build.ps1 -SqlDeveloperPath "C:\dev\sqldeveloper" -JdkPath "C:\tools\jdk-21"
```

결과는 `dist/SQLDeveloperConfigurator.exe`입니다. Java 에이전트와 패처를 컴파일해 실행 파일에 내장합니다. Oracle JAR과 JDK는 배포물에 포함하지 않습니다.

## 테스트

```powershell
.\test.ps1 -SqlDeveloperPath "C:\dev\sqldeveloper"
.\test.ps1 -SqlDeveloperPath "C:\dev\sqldeveloper" -NoBuild
```

`out/` 아래의 분리된 설치본·설정으로 설치, 재적용, 복구, 50pt 글꼴, 결과표 행 높이·선택, 편집기 줄바꿈·좌표·편집, 수평선과 환경설정을 검사합니다.

글꼴 동기화 검사는 실제 Oracle 글꼴 패널의 진입·이동·취소·적용 경로를 계측 에이전트와 함께 실행합니다. 24/50/7/34pt 변경 후 기존·신규 결과표, 행 높이·선택·원래 렌더러·UTF-8 설정 보존과 별도 bundle 클래스 로더를 확인합니다. 테스트 데이터와 사용자 설정은 `out/`에만 생성합니다.

실행 중인 설치본의 환경설정 등록은 다음과 같이 검사합니다.

```powershell
.\scripts\verify-live.ps1 -SqlDeveloperPath "C:\dev\sqldeveloper" -SqlDeveloperProcessId 12345
```

실제 코드 편집기 트리의 자식 생성과 패널 로딩을 검사합니다. 화면 입력이나 SQL 문서 변경은 하지 않습니다.

## 소스 구성

| 경로 | 내용 |
| --- | --- |
| `src/wpf/` | 설정 도구, 설치본 검색, 적용·백업·복구 |
| `src/java/agent/` | 결과표 스타일, 환경설정 패널 및 트리 연결 |
| `src/java/bootstrap/` | 편집기 표시와 좌표 처리 |
| `src/java/instrumentation/` | Oracle 편집기·환경설정 메서드 연결 |
| `src/java/patcher/` | 글꼴 크기 제한 확장 |
| `tests/` | C#·Java 검사 및 문서 이미지 렌더러 |
| `scripts/` | 빌드 도우미, 이미지 및 배포 패키지 생성 |

호환성을 위해 내부 네임스페이스와 기존 사용자 설정 폴더의 `Font50` 이름을 유지합니다.
