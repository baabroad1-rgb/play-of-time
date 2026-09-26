#!/usr/bin/env python3
"""Собирает docs/COMMIT_LOG.md из истории git.

Запускается из .github/workflows/commit-log.yml при каждом попадании в main.
Руками результат не правят — он перезаписывается целиком.

Что попадает в журнал: дата, заголовок изменения и его полное описание.
Служебные коммиты (автобамп версии, обновление самого журнала) в текст не
идут — они только шумят; их номера версий используются, чтобы подписать,
какая версия приложения была на тот момент.
"""

import os
import re
import subprocess
from collections import OrderedDict

SEP = "\x1e"      # между полями
REC = "\x1d"      # между коммитами

# Коммиты, которые в журнал не попадают: сделаны автоматикой, смысла не несут.
NOISE = (
    re.compile(r"^Автобамп версии", re.I),
    re.compile(r"^Обновить автоматический журнал", re.I),
    re.compile(r"^Merge (branch|pull request|remote)", re.I),
)

# Строки внутри описания, которые в журнале ни к чему.
DROP_LINE = re.compile(r"^(Co-Authored-By|Claude-Session|Signed-off-by):", re.I)

MONTHS = {
    "01": "января", "02": "февраля", "03": "марта", "04": "апреля",
    "05": "мая", "06": "июня", "07": "июля", "08": "августа",
    "09": "сентября", "10": "октября", "11": "ноября", "12": "декабря",
}


def git(*args):
    return subprocess.run(
        ("git",) + args, check=True, capture_output=True, text=True
    ).stdout


def commits():
    raw = git(
        "log", "--reverse", "--date=short",
        f"--pretty=format:%H{SEP}%ad{SEP}%s{SEP}%b{REC}",
    )
    for chunk in raw.split(REC):
        chunk = chunk.strip("\n")
        if not chunk:
            continue
        parts = chunk.split(SEP)
        if len(parts) < 3:
            continue
        sha, date, subject = parts[0], parts[1], parts[2]
        body = parts[3] if len(parts) > 3 else ""
        yield sha, date, subject, body


def human_date(iso):
    y, m, d = iso.split("-")
    return f"{int(d)} {MONTHS.get(m, m)} {y}"


def clean_body(body):
    lines = []
    for line in body.splitlines():
        if DROP_LINE.match(line.strip()):
            continue
        lines.append(line.rstrip())
    # убрать пустые строки по краям и склеить подряд идущие
    while lines and not lines[0].strip():
        lines.pop(0)
    while lines and not lines[-1].strip():
        lines.pop()
    out = []
    for line in lines:
        if not line.strip() and out and not out[-1].strip():
            continue
        out.append(line)
    return out


def repo_url():
    """Адрес репозитория для ссылок на коммиты.

    В Actions он лежит в GITHUB_REPOSITORY; при запуске руками достаём из
    настроек remote. Относительные ссылки тут не годятся: GitHub считает их
    от пути файла и уводит внутрь blob/.
    """
    slug = os.environ.get("GITHUB_REPOSITORY")
    if not slug:
        url = git("remote", "get-url", "origin").strip()
        m = re.search(r"github\.com[:/](.+?)(?:\.git)?$", url)
        slug = m.group(1) if m else ""
    server = os.environ.get("GITHUB_SERVER_URL", "https://github.com")
    return f"{server}/{slug}" if slug else ""


def main():
    # Версия приложения берётся из служебных коммитов автобампа: они
    # называются «Автобамп версии после сборки vX.Y», и всё, что было до
    # следующего такого коммита, собиралось под этой версией.
    by_date = OrderedDict()
    for sha, date, subject, body in commits():
        version = None
        bump = re.match(r"^Автобамп версии после сборки (v[\d.]+)", subject, re.I)
        if bump:
            version = bump.group(1)
        if any(p.match(subject) for p in NOISE):
            if version:
                by_date.setdefault(date, {"version": None, "items": []})
                by_date[date]["version"] = version
            continue
        by_date.setdefault(date, {"version": None, "items": []})
        by_date[date]["items"].append((sha, subject, clean_body(body)))

    base = repo_url()

    print("# Журнал изменений (собирается сам)")
    print()
    print("Этот файл перегенерируется из истории git при каждом изменении,")
    print("попавшем в `main` — править его руками бессмысленно, правки затрёт.")
    print("Что-то объяснить своими словами — в `CHANGELOG.md`.")
    print()
    print("Сверху самое свежее. Ссылка в конце каждой записи ведёт на точный")
    print("список изменённых строк.")
    print()

    total = 0
    for date in reversed(list(by_date)):
        entry = by_date[date]
        if not entry["items"]:
            continue
        head = f"## {human_date(date)}"
        if entry["version"]:
            head += f" — версия {entry['version']}"
        print(head)
        print()
        for sha, subject, body in reversed(entry["items"]):
            total += 1
            print(f"### {subject}")
            print()
            for line in body:
                print(line)
            if body:
                print()
            print(f"[изменённые строки]({base}/commit/{sha})")
            print()

    print("---")
    print()
    print(f"Всего записей: {total}.")


if __name__ == "__main__":
    main()
