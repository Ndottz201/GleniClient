@echo off
setlocal
title GleniClient GitHub Updater

set REPO_URL=https://github.com/Ndottz201/GleniClient.git

echo.
echo  ================================================
echo     GleniClient ^|^| GitHub Auto-Updater
echo  ================================================
echo.
echo  Folder : %CD%
echo  Repo   : %REPO_URL%
echo.
set /p CONFIRM="  Push this folder to GitHub? (Y/N): "
if /i not "%CONFIRM%"=="Y" (
    echo  Cancelled.
    pause
    exit /b 0
)

echo.
echo  [1/5] Setting up git...
git init
git config core.longpaths true
git config user.name "Ndottz201"
git config user.email "glenixhati@gmail.com"

echo  [2/5] Creating .gitignore...
(
echo .gradle/
echo build/
echo .vscode/
echo .idea/
echo *.class
echo *.jar
echo !gradle/wrapper/gradle-wrapper.jar
echo out/
echo run/
echo logs/
) > .gitignore

echo  [3/5] Setting remote...
git remote remove origin 2>nul
git remote add origin %REPO_URL%

echo  [4/5] Staging all files...
git add -A
if errorlevel 1 (
    echo  [ERROR] Failed to stage files.
    pause
    exit /b 1
)
git diff --cached --quiet 2>nul
if errorlevel 1 (
    git commit -m "update: new project - %DATE% %TIME%"
) else (
    echo  No new changes to commit.
)

echo  [5/5] Pushing to GitHub...
git branch -M main
git push origin main --force
if errorlevel 1 (
    echo.
    echo  [ERROR] Push failed. Make sure your token is saved in Credential Manager.
    echo.
    pause
    exit /b 1
)
git push origin main:master --force 2>nul

echo.
echo  ================================================
echo     SUCCESS! GitHub repo has been updated.
echo  ================================================
echo.
pause
exit /b 0