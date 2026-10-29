# 외부 소프트웨어

이 저장소의 자체 작성 소스에는 [MIT 라이선스](LICENSE)가 적용됩니다.

| 항목 | 사용 방식 | 저장소/배포 파일 포함 여부 |
| --- | --- | --- |
| Oracle SQL Developer | 설치된 26.2.0.186.2220의 API를 빌드·실행 시 참조 | Oracle JAR·설치 파일 미포함 |
| Java / JDK | Java 17 바이트코드 컴파일, 실행, JDK 내부 ASM API 참조 | JDK·JRE·ASM 라이브러리 미포함 |
| Microsoft .NET Framework / WPF | Windows 설정 도구 빌드·실행 | 런타임 미포함 |
| 글꼴 | 각 PC에 설치된 글꼴 사용 | 글꼴 파일 미포함 |
| Eclipse Paper | Java2D 노트·질감 렌더링 코드를 Swing용으로 이식, 생성된 종이 이미지 재사용 | 자체 소스·이미지 포함, [MIT 원문](docs/ECLIPSE-PAPER-LICENSE.txt) |

SQL Developer, Java, Microsoft 및 관련 상표는 각 소유자의 자산입니다. 이 프로젝트는 Oracle의 공식 제품이 아닙니다. Oracle SQL Developer는 별도로 설치해야 하며 해당 소프트웨어의 이용 조건은 이 저장소의 MIT 라이선스로 대체되지 않습니다.

앱 아이콘은 `src/wpf/MakeIcon.cs`의 도형과 텍스트로 생성합니다. Oracle의 원본 아이콘 파일은 배포하지 않습니다.
