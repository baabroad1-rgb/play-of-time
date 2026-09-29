# Готовит face2_hair.html к публикации в Artifact на claude.ai.
#
# Зачем скрипт, а не вторая копия файла в репозитории: копия молча
# расходится с оригиналом — на этом проекте так уже трижды обжигались
# (см. README, «Что изменилось с переездом на git»). Копия делается из
# оригинала в момент публикации и нигде не хранится.
#
# Что меняется. Artifact сам оборачивает страницу в свой скелет, поэтому
# свои doctype/html/head/body не нужны: остаётся <title>, <style> и
# содержимое <body>. Плюс липкая шапка на телефоне уезжает ниже системной
# полосы, и явно объявляется светлая схема — палитра у страницы одна,
# приложенческая, тёмной темы у неё нет по замыслу.
#
#   python3 docs/prototypes/make_artifact_copy.py > /tmp/face2_artifact.html
#
# Дальше опубликовать получившийся файл инструментом Artifact. Ссылка на
# уже опубликованную страницу — в docs/FACE_V2_DESIGN.md; чтобы обновить
# её, а не завести вторую, публиковать с тем же url.

import pathlib, sys

SRC = pathlib.Path(__file__).with_name("face2_hair.html")
src = SRC.read_text(encoding="utf-8")

head = src[src.index("<title>"):src.index("</head>")]
body = src[src.index("<body>") + len("<body>"):src.index("</body>")]

out = head.rstrip() + "\n" + body
out = out.replace("<title>Морда 2.0 — обсуждение</title>", "<title>Морда 2.0</title>")
out = out.replace(".stage{position:sticky; top:0;",
                  ".stage{position:sticky; top:env(safe-area-inset-top, 0px);")
out = out.replace(":root{\n    --surface:", ":root{\n    color-scheme: light;\n    --surface:")

sys.stdout.write(out)
