@echo off
chcp 65001 >nul
echo.
echo ╔══════════════════════════════════════╗
echo ║   CatGuard — Завантаження на GitHub  ║
echo ╚══════════════════════════════════════╝
echo.

REM ── Перевірити чи є git ─────────────────────────────────────────────────────
git --version >nul 2>&1
IF ERRORLEVEL 1 (
    echo [ПОМИЛКА] Git не знайдений. Скачай з: https://git-scm.com
    pause
    exit /b 1
)

REM ── Запитати дані ───────────────────────────────────────────────────────────
set /p GITHUB_USER=Введи свій GitHub нікнейм (наприклад Maksym5653): 
set /p REPO_NAME=Введи назву репозиторію (наприклад CatGuardApp): 
set /p GIT_EMAIL=Введи свій email від GitHub: 

echo.
echo Нікнейм:    %GITHUB_USER%
echo Репо:       %REPO_NAME%
echo Email:      %GIT_EMAIL%
echo.
set /p CONFIRM=Все правильно? (y/n): 
IF /I NOT "%CONFIRM%"=="y" goto :EOF

REM ── Налаштувати git ──────────────────────────────────────────────────────────
git config --global user.email "%GIT_EMAIL%"
git config --global user.name "%GITHUB_USER%"
git config --global core.autocrlf true

REM ── Ініціалізувати репо ──────────────────────────────────────────────────────
IF NOT EXIST ".git" (
    echo.
    echo [1/5] Ініціалізація git...
    git init
    git branch -M main
) ELSE (
    echo [1/5] Git вже ініціалізований
)

REM ── Додати всі файли ────────────────────────────────────────────────────────
echo [2/5] Додаю файли...
git add .

REM ── Зробити коміт ───────────────────────────────────────────────────────────
echo [3/5] Коміт...
git commit -m "CatGuard initial commit — автозбірка APK"

REM ── Підключити GitHub ───────────────────────────────────────────────────────
echo [4/5] Підключаю GitHub...
git remote remove origin >nul 2>&1
git remote add origin https://github.com/%GITHUB_USER%/%REPO_NAME%.git

REM ── Push ────────────────────────────────────────────────────────────────────
echo [5/5] Завантажую на GitHub...
echo.
echo Зараз відкриється браузер для авторизації GitHub...
git push -u origin main --force

echo.
IF ERRORLEVEL 1 (
    echo [ПОМИЛКА] Push не вдався.
    echo Переконайся що репозиторій %REPO_NAME% існує на github.com/%GITHUB_USER%
    echo Створи його тут: https://github.com/new
) ELSE (
    echo ══════════════════════════════════════════════
    echo  ✅ УСПІХ! Код завантажено на GitHub!
    echo.
    echo  📦 Щоб отримати APK:
    echo  1. Відкрий: https://github.com/%GITHUB_USER%/%REPO_NAME%/actions
    echo  2. Чекай 5-10 хвилин (зелена галочка)
    echo  3. APK буде у вкладці Releases або Artifacts
    echo ══════════════════════════════════════════════
)

echo.
pause
