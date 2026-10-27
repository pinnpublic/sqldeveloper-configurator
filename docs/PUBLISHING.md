# GitHub에 올리기

**이 프로젝트 폴더만** GitHub 저장소의 루트로 사용합니다. 상위 SQL Developer 설치 폴더를 저장소로 만들지 않습니다. Oracle 설치 파일과 사용자 백업은 이 소스 폴더에 포함되어 있지 않습니다.

## 웹으로 올리기

1. GitHub에서 빈 저장소를 만듭니다.
2. 이 폴더의 `README.md`, `LICENSE`, `src`, `tests`, `scripts`, `docs`와 나머지 루트 파일을 업로드합니다.
3. `out`과 `dist`는 올리지 않습니다. 소스 ZIP을 사용하면 이 두 폴더는 이미 제외됩니다.

## Git으로 올리기

```powershell
git add .
git commit -m "Initial release of SQL Developer Configurator"
git branch -M main
git remote add origin <GitHub에서 만든 저장소 URL>
git push -u origin main
```

`.gitignore`는 빌드 결과, 백업, 사용자 설정과 접속 설정 파일을 제외합니다. 작성자 이름·이메일 설정과 GitHub 로그인은 본인 계정으로 진행합니다.

## 실행 파일 배포

```powershell
.\build.ps1 -SqlDeveloperPath "C:\dev\sqldeveloper"
```

GitHub의 **Releases**에서 버전 태그(예: `v3.4.0`)를 만들고 `dist/SQLDeveloperConfigurator.exe`와 `dist/SHA256SUMS.txt`를 첨부합니다. 빌드 결과 ZIP을 사용하는 경우 ZIP도 함께 첨부할 수 있습니다. Oracle JAR이나 JDK는 첨부하지 않습니다.

```powershell
.\scripts\package-release.ps1
```

`dist/SQLDeveloperConfigurator-3.4.0-win.zip`에 실행 파일, 간단 매뉴얼, MIT 라이선스와 외부 소프트웨어 안내가 담깁니다.

## 소스 ZIP 만들기

```powershell
.\scripts\export-source.ps1
```

결과는 `dist/SQLDeveloperConfigurator-source.zip`입니다. 소스, README, 매뉴얼, MIT 라이선스와 문서 이미지가 포함되고, 빌드 결과·Oracle 설치 파일·사용자 설정은 제외됩니다.
