@echo off
rem ---------------------------------------------------------------------
rem  MES-MAX backend launcher (Windows)
rem
rem  Usage
rem    run.cmd                             H2 file DB, first run creates
rem                                        schema + demo data automatically
rem    set MES_RESET=-Dmesmax.db.reset=true  then run.cmd   -> reset demo data
rem    run.cmd -Dany.maven.option=value     extra Maven args are forwarded
rem
rem  IMPORTANT: this file must stay PURE ASCII on purpose.
rem  cmd.exe parses .cmd files with the active OEM code page (GBK on zh-CN)
rem  and switching code pages mid-file is unreliable: a UTF-8 Chinese
rem  comment read as GBK loses byte alignment and swallows characters of
rem  the next command ("setlocal" became "tlocal", the script died).
rem  Chinese documentation lives in README.md / docs/06-deployment manual.
rem
rem  Token secret: the backend itself resolves it (no launcher logic needed)
rem    1. MES_JWT_SECRET env var set -> use it (production / multi-instance)
rem    2. otherwise: read/generate .jwt-secret in the working directory
rem  So a restart keeps previously issued tokens valid, while no fixed secret
rem  is ever committed to the repository.
rem ---------------------------------------------------------------------
setlocal

set MAVEN_OPTS=-Dfile.encoding=UTF-8
set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8

echo [MES-MAX] backend launcher
echo   - default: H2 file database, first run creates schema + demo data
echo   - reset:   set MES_RESET=-Dmesmax.db.reset=true   (then run this file)
echo   - secret:  env MES_JWT_SECRET, else auto-persisted .jwt-secret
echo.

if "%MES_RESET%"=="" (
  mvn -q clean spring-boot:run %*
) else (
  echo [MES-MAX] resetting database, then starting ...
  mvn -q clean spring-boot:run -Dspring-boot.run.jvmArguments="%MES_RESET% -Dfile.encoding=UTF-8" %*
)

endlocal
