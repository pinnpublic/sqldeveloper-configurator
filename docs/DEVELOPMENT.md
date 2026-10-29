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

노트 배경 검사는 실제 Oracle 편집기에서 줄바꿈 켜짐/꺼짐, 7/24/50pt, 추가 줄 간격 0/30/200%, 커서·클릭 좌표·선택·SQL 불변을 검사합니다. 12종 배경·10종 선·36종 질감을 렌더링하고 native 행 번호 여백의 좌표와 스크롤, 환경설정의 이동·취소·적용·재열기·유효성 검사를 확인합니다. 설정은 별도 `notebook-settings.json`에 저장하므로 Configurator 재적용으로 덮어쓰지 않습니다.

`EditorResponsivenessTests`는 마우스 좌표와 별도로 Oracle의 실제 `caret-down/up`, `selection-down/up`, Page Up/Down 동작을 호출합니다. 줄바꿈 켜짐/꺼짐 × 7/24/50pt × 줄 간격 0/30/90/200%를 검사합니다. 100/1,000줄의 좌표·그리기·편집 시간을 기록하고 10,000줄의 서로 다른 문장 및 구분선 다수의 반응성을 검사합니다. native `updateMetrics`는 변경이 없는 좌표 조회에서도 호출되므로 이 메서드 진입을 캐시 무효화 조건으로 사용하면 안 됩니다.

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
