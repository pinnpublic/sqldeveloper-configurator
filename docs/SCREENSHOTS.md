# 문서 이미지 안내

README의 이미지는 동작하는 제품 코드를 이용한 **UI 렌더링 예시**입니다. 실제 DB 조회 결과나 사용자 데스크톱 캡처로 가장하지 않습니다.

| 파일 | 생성 대상 | 입력과 설정 |
| --- | --- | --- |
| `images/icon.png` | `MakeIcon.cs`의 자체 아이콘 | 벡터 도형과 Aa 텍스트 |
| `images/configurator.png` | 실제 WPF `App` / `MainWindow.xaml` | 자동 검색 후 24pt, 여백 8px. 숨겨진 테스트 모드에서 화면 내용을 렌더링 |
| `images/preferences.png` | 실제 `EditorPreferencesPanel` / Oracle `DefaultTraversablePanel` | 테스트 설정 폴더, 두 기능 켜기, 2px 실선 |
| `images/editor-wrap.png` | 실제 Oracle `BasicEditorPane` / `BasicView`와 표시 어댑터 | 문서용 SQL, 28pt, 줄바꿈·수평선 켜기 |
| `images/rule-styles.png` | 실제 Oracle 편집기 네 개 | `---` 단독 줄, 네 가지 선 모양 |
| `images/results-50pt.png` | 실행 중인 Developer 안의 실제 Oracle `ResultSetTable`과 기본 셀 렌더러 | 메모리에 생성한 `row_01`~`row_03`, 50pt, 위·아래 8px |

사용자 SQL 파일, DB 접속 정보, 계정, 비밀번호 또는 실제 테이블 데이터는 이미지에 사용하지 않았습니다. 예제 데이터는 [DocumentationOracleGrid.java](../tests/java/io/github/sqldevgrid/DocumentationOracleGrid.java)에 공개되어 있습니다. 이 모델의 DB 준비 메서드는 호출되면 예외를 던지도록 되어 있습니다.

## 다시 생성하기

```powershell
.\scripts\screenshots.ps1 -SqlDeveloperPath "C:\dev\sqldeveloper"
```

결과표 이미지를 제외한 UI 이미지를 생성합니다. WPF 창을 표시하거나 키보드·마우스를 조작하지 않습니다. 별도 프로세스의 `out/documentation-*-appdata`를 사용하므로 실제 사용자 설정도 바꾸지 않습니다.

실제 Oracle 결과표 이미지를 새로 생성하려면 Configurator가 적용된 SQL Developer를 실행하고 프로세스 ID를 지정합니다. 워크시트가 열려 있으면 충분하며 DB 접속은 필요하지 않습니다.

```powershell
.\scripts\screenshots.ps1 -SqlDeveloperPath "C:\dev\sqldeveloper" -SqlDeveloperProcessId 12345
```

이 옵션은 실행 중인 JVM에 문서용 에이전트를 연결해 **분리된 메모리 결과표만** 생성하고 렌더링합니다. 사용자가 보고 있는 결과표나 편집기에는 연결하지 않고, 화면을 전환하거나 쿼리를 실행하지 않습니다. 시스템의 JVM 연결 권한에 따라 같은 사용자 또는 필요한 권한으로 실행해야 합니다.

글꼴과 Windows 테마, 화면 배율에 따라 다른 PC에서는 이미지 모양이 달라질 수 있습니다. 글꼴 파일은 저장소에 포함하지 않습니다.
